#!/usr/bin/env python3
"""Check actual merged Android permissions and transport settings for an ad mode."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
AD_PERMISSIONS = {
    "com.google.android.gms.permission.AD_ID",
    "android.permission.READ_BASIC_PHONE_STATE",
}
CORE_PERMISSIONS = {
    "android.permission.INTERNET",
    "com.android.vending.BILLING",
    "android.permission.POST_NOTIFICATIONS",
    "com.google.android.c2dm.permission.RECEIVE",
}


def verify(path, mode, environment, build_type, baseline=None):
    manifest = ET.parse(path).getroot()
    permissions = {node.get(ANDROID + "name") for node in manifest.findall("uses-permission")}
    expected_ads = mode in {"demo", "test", "live"}
    actual_ads = permissions & AD_PERMISSIONS
    assert actual_ads == (AD_PERMISSIONS if expected_ads else set()), (
        f"Unexpected advertising permissions in {mode}: {sorted(actual_ads)}"
    )
    assert CORE_PERMISSIONS <= permissions, "Required network/Billing/push permission was removed"
    if baseline is not None:
        original = ET.parse(baseline).getroot()
        original_permissions = {node.get(ANDROID + "name") for node in original.findall("uses-permission")}
        expected = original_permissions if expected_ads else original_permissions - AD_PERMISSIONS
        assert permissions == expected, "Permissions other than the approved pair changed"
    application = manifest.find("application")
    assert application is not None
    assert application.get(ANDROID + "usesCleartextTraffic") == (
        "true" if environment == "dev" else "false"
    ), "Environment transport protection changed"
    if build_type == "release":
        assert application.get(ANDROID + "debuggable", "false") == "false"
    package = "com.hienthai.fastowin" + (".dev" if environment == "dev" else "")
    assert manifest.get("package") == package, "Wrong application environment"
    return {
        "manifest": str(path),
        "mode": mode,
        "environment": environment,
        "buildType": build_type,
        "adPermissions": sorted(actual_ads),
        "permissionCount": len(permissions),
        "transportPolicyPreserved": True,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--mode", choices=["default", "disabled", "demo", "test", "live"], required=True)
    parser.add_argument("--environment", choices=["dev", "prod"], required=True)
    parser.add_argument("--build-type", choices=["debug", "release"], required=True)
    parser.add_argument("--baseline", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.manifest, args.mode, args.environment, args.build_type, args.baseline)))


if __name__ == "__main__":
    main()
