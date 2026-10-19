#!/usr/bin/env python3
"""Resolve and run a disjoint group of instrumentation tests."""

import argparse
import subprocess


MODULE_GROUPS = {
    "akka": {"akka", "akkaHttp", "akkaGrpc", "alpakkaKafka", "lagom"},
    "pekko": {"pekko", "pekkoHttp", "pekkoGrpc", "pekkoConnectorsKafka"},
    "play": {"play"},
}


def task_group(task):
    module = task.removeprefix("instrumentation.").split("[", 1)[0].split(".", 1)[0]
    for group, modules in MODULE_GROUPS.items():
        if module in modules:
            return group
    return "other"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("group", choices=[*MODULE_GROUPS, "other"])
    parser.add_argument("--list", action="store_true", help="List tasks without running tests")
    args = parser.parse_args()

    resolved = subprocess.check_output(
        ["./mill", "--no-daemon", "resolve", "instrumentation.__.testForked"],
        text=True,
    )
    tasks = [
        line.strip()
        for line in resolved.splitlines()
        if line.strip().startswith("instrumentation.")
        and line.strip().endswith(".testForked")
    ]
    selected = [task for task in tasks if task_group(task) == args.group]
    if not selected:
        parser.exit(1, f"No instrumentation tests resolved for group {args.group!r}\n")

    print("\n".join(selected), flush=True)
    if not args.list:
        selectors = []
        for task in selected:
            if selectors:
                selectors.append("+")
            selectors.append(task)
        subprocess.run(["./mill", "--no-daemon", "-j", "1", *selectors], check=True)


if __name__ == "__main__":
    main()
