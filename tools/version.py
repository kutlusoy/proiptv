#!/usr/bin/env python3
"""Reads and checks the version of the app. The single source is app/version.txt."""
import argparse
import re
import sys

PATTERN = re.compile(r"^(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.]+)?$")


def parse(version):
    m = PATTERN.match(version)
    if not m:
        raise ValueError(f"'{version}' does not look like 1.2.3 or 1.2.3-rc1")
    major, minor, patch = (int(g) for g in m.groups()[:3])
    if minor > 999 or patch > 999 or major > 2000:
        raise ValueError("minor and patch must be at most 999 and major at most 2000, the version code scheme has to be extended")
    suffix = m.group(4) or ""
    return {
        "version": version,
        "tag": "v" + version,
        "suffix": suffix,
        "prerelease": "true" if suffix else "false",
        "version_code": str(major * 1_000_000 + minor * 1_000 + patch),
    }


def selftest():
    assert parse("0.0.1")["version_code"] == "1"
    assert parse("0.1.0")["version_code"] == "1000"
    assert parse("1.2.3")["version_code"] == "1002003"
    assert parse("1.0.0-rc1")["prerelease"] == "true"
    assert parse("1.0.0")["prerelease"] == "false"
    ordered = ["0.0.1", "0.0.2", "0.0.3", "0.1.0", "0.9.9", "1.0.0", "1.0.1", "1.1.0", "2.0.0"]
    codes = [int(parse(v)["version_code"]) for v in ordered]
    assert codes == sorted(codes) and len(set(codes)) == len(codes)
    assert max(codes) < 2_100_000_000
    for bad in ["1.0", "v1.0.0", "1.0.0.0", "1.1000.0", "1.0.1000", "2001.0.0", "a.b.c", ""]:
        try:
            parse(bad)
        except ValueError:
            continue
        raise AssertionError(f"accepted {bad!r}")
    print("version selftest ok")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("version", nargs="?", help="a version or a tag such as v1.2.3, defaults to the content of the file")
    ap.add_argument("--file", default="app/version.txt", help="the version file, it must match the version argument")
    ap.add_argument("--selftest", action="store_true")
    args = ap.parse_args()
    if args.selftest:
        selftest()
        return
    try:
        declared = open(args.file).read().strip()
        wanted = args.version.removeprefix("v") if args.version else declared
        info = parse(wanted)
        if declared != wanted:
            raise ValueError(f"{args.file} says {declared} but the version is {wanted}, update the file first")
    except (ValueError, OSError) as e:
        print(f"version: {e}", file=sys.stderr)
        sys.exit(1)
    for key, value in info.items():
        print(f"{key}={value}")


if __name__ == "__main__":
    main()
