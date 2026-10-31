"""Remote Mac identity follows the saved session, not the Android installation."""
import asyncio
import base64
from types import SimpleNamespace

import pytest
from findmy.reports import AppleAccount
import main
from remote_mac import RemoteMacAnisetteProvider


def fixture_headers():
    return {
        "X-Apple-I-MD": base64.b64encode(b'o' * 28).decode(),
        "X-Apple-I-MD-M": base64.b64encode(b'm' * 60).decode(),
        "X-Apple-I-MD-LU": base64.b64encode(b'0123456789ABCDEF0123456789ABCDEF').decode(),
        "X-Mme-Device-Id": "00006000-0123456789ABCDEF",
        "X-Apple-I-SRL-NO": "C02TEST123456",
        "X-Apple-I-MD-RINFO": "17106176",
        "X-MMe-Client-Info": "<MacBookPro13,2> <macOS;13.1;22C65> <com.apple.AuthKit/1 (com.apple.akd/1.0)>",
    }


def mock_server(provider, headers):
    async def get(*args, **kwargs):
        return SimpleNamespace(json=lambda: dict(headers))
    provider._http.get = get


def test_complete_identity_reaches_headers_and_survives_restore():
    provider = RemoteMacAnisetteProvider("https://example.invalid", timeout=30)
    mock_server(provider, fixture_headers())
    account = AppleAccount(provider)
    account._evt_loop.run_until_complete(provider.prepare())
    profile = provider.mac_profile
    account._asyncacc._uid = profile["uid"]
    account._asyncacc._devid = profile["devid"]
    actual = account.get_anisette_headers(with_client_info=True)
    for key in ("X-Apple-I-MD-LU", "X-Mme-Device-Id", "X-Apple-I-SRL-NO", "X-Apple-I-MD-RINFO"):
        assert actual[key] == fixture_headers()[key]
    assert provider.identity.platform == "<MacBookPro13,2> <macOS;13.1;22C65>"
    stored = account.to_json()
    assert stored["anisette"]["remote_mac_profile"] == profile
    assert "X-Apple-I-MD" not in str(stored["anisette"])
    restored_provider = RemoteMacAnisetteProvider.from_saved(stored["anisette"])
    mock_server(restored_provider, fixture_headers())
    restored = AppleAccount(restored_provider, state_info=stored)
    assert restored.get_anisette_headers() == account.get_anisette_headers()
    main.closeAccount(restored)
    main.closeAccount(account)


def test_server_cannot_silently_change_a_saved_machine():
    async def check():
        headers = fixture_headers()
        provider = RemoteMacAnisetteProvider("https://example.invalid",
                                             mac_profile=RemoteMacAnisetteProvider.profile(headers))
        headers["X-Mme-Device-Id"] = "00006000-FEDCBA9876543210"
        mock_server(provider, headers)
        with pytest.raises(ValueError, match="identity changed"):
            await provider.prepare()
        await provider.close()
    asyncio.run(check())


def test_legacy_two_header_server_stays_legacy():
    async def check():
        headers = fixture_headers()
        provider = RemoteMacAnisetteProvider("https://example.invalid")
        mock_server(provider, {k: headers[k] for k in ("X-Apple-I-MD", "X-Apple-I-MD-M")})
        await provider.prepare()
        assert provider.mac_profile is None
        assert "remote_mac_profile" not in provider.to_json()
        await provider.close()
    asyncio.run(check())


@pytest.mark.parametrize("field,value", [("X-Apple-I-MD", "bad"),
                                         ("X-Apple-I-MD-LU", "!not base64!"),
                                         ("X-Apple-I-MD-RINFO", ""),
                                         ("X-MMe-Client-Info", "<Unknown>")])
def test_malformed_server_responses_fail_before_login(field, value):
    async def check():
        headers = fixture_headers()
        headers[field] = value
        provider = RemoteMacAnisetteProvider("https://example.invalid")
        mock_server(provider, headers)
        with pytest.raises(ValueError):
            await provider.prepare()
        await provider.close()
    asyncio.run(check())
