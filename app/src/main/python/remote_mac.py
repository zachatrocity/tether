"""Mac-lineage remote Anisette with persistent, server-owned device identity.

Legacy two-header servers retain their old behavior. A server advertising the complete
Mac profile must keep it stable: a changed machine requires a new login, not a silent fallback.
"""
from __future__ import annotations

import base64
import re
import time
from typing import Any, cast

from findmy.reports.anisette import DeviceIdentity, RemoteAnisetteProvider, RemoteAnisetteMapping


class RemoteMacMapping(RemoteAnisetteMapping, total=False):
    remote_mac_profile: dict[str, Any]


class RemoteMacAnisetteProvider(RemoteAnisetteProvider):
    def __init__(self, url: str, *, mac_profile: dict | None = None, **kwargs: Any) -> None:
        super().__init__(url, **kwargs)
        self.mac_profile = mac_profile
        if mac_profile:
            self._use_profile(mac_profile)

    @staticmethod
    def profile(data: dict) -> dict | None:
        client = data.get("X-MMe-Client-Info", data.get("X-Mme-Client-Info", ""))
        if not client:
            return None
        # This helper's supported macOS profile. Do not invent CFNetwork/Darwin versions
        # from arbitrary server strings: they must describe the same actual OS release.
        match = re.fullmatch(
            r"<(Mac[^<>]+)> <macOS;13\.1;22C65> <com\.apple\.AuthKit/1 \(com\.apple\.akd/1\.0\)>", client)
        if not match:
            raise ValueError("Unsupported remote Mac identity profile")
        required = ("X-Apple-I-MD-LU", "X-Mme-Device-Id", "X-Apple-I-SRL-NO", "X-Apple-I-MD-RINFO")
        if any(not isinstance(data.get(k), str) or not data[k] for k in required):
            raise ValueError("Remote Mac server returned an incomplete identity")
        uid = base64.b64decode(data[required[0]], validate=True).decode("ascii")
        devid = data[required[1]]
        serial = data[required[2]]
        routing = data[required[3]]
        if (not re.fullmatch(r"[A-Za-z0-9-]{16,64}", uid)
                or not re.fullmatch(r"[A-Za-z0-9-]{16,64}", devid)
                or not re.fullmatch(r"[A-Za-z0-9]{1,32}", serial)
                or not routing.isdigit()):
            raise ValueError("Remote Mac server returned a malformed identity")
        return {"model": match[1], "uid": uid, "devid": devid, "serial": serial, "routing": routing}

    def _use_profile(self, profile: dict) -> None:
        self._identity = DeviceIdentity(model=profile["model"], os_name="macOS",
                                        os_version="13.1", os_build="22C65",
                                        cfnetwork="1404.0.5", darwin="22.2.0")
        self._serial = profile["serial"]

    async def prepare(self) -> None:
        if self._anisette_data is None or time.time() >= self._anisette_data_expires_at:
            response = await self._http.get(self._server_url, auto_retry=False)
            data = response.json()
            if not isinstance(data, dict):
                raise ValueError("Remote server did not return an Anisette object")
            for key, size in (("X-Apple-I-MD", 28), ("X-Apple-I-MD-M", 60)):
                if len(base64.b64decode(data.get(key, ""), validate=True)) != size:
                    raise ValueError("Remote server returned invalid Anisette data")
            profile = self.profile(data)
            if self.mac_profile is not None and profile != self.mac_profile:
                raise ValueError("Remote Mac identity changed; sign in again with the intended server")
            if profile:
                self.mac_profile = profile
                self._use_profile(profile)
            self._anisette_data = data
            self._anisette_data_expires_at = time.time() + 20

    @property
    def router(self) -> str:
        return self.mac_profile["routing"] if self.mac_profile else super().router

    @property
    def timestamp(self) -> str:
        return (self._anisette_data or {}).get("X-Apple-I-Client-Time") or super().timestamp

    async def get_headers(self, user_id: str, device_id: str, serial: str | None = None,
                          with_client_info: bool = False) -> dict[str, str]:
        await self.prepare()
        if self.mac_profile:
            if (user_id != self.mac_profile["uid"] or device_id != self.mac_profile["devid"]):
                raise ValueError("Account and remote Mac identities disagree; sign in again")
        return await super().get_headers(user_id, device_id, serial, with_client_info)

    def to_json(self, dst=None, /) -> RemoteMacMapping:
        from findmy.util.files import save_and_return_json
        data: dict[str, Any] = dict(super().to_json())
        if self.mac_profile:
            data["remote_mac_profile"] = self.mac_profile
        return cast(RemoteMacMapping, save_and_return_json(data, dst))

    @classmethod
    def from_saved(cls, data: dict):
        return cls(data["url"], mac_profile=data["remote_mac_profile"],
                   timeout=data.get("timeout", 30))
