import sys
import os
import re
import argparse
import fnmatch
import subprocess

def get_changed_files():
    try:
        # Get staged and unstaged changes
        result = subprocess.run(['git', 'diff', 'HEAD', '--name-only'], capture_output=True, text=True)
        files = result.stdout.splitlines()

        # Get untracked files
        result = subprocess.run(['git', 'ls-files', '--others', '--exclude-standard'], capture_output=True, text=True)
        files.extend(result.stdout.splitlines())

        return list(set(files))
    except Exception as e:
        # Fallback if git is not available or not a repo
        return []

def validate_scope(scope, changed_files):
    # Default: allow all unless it's a restricted scope
    allowed_patterns = []

    if scope == 'somente-documentacao':
        allowed_patterns = [
            'README.md', 'docs/*', '*.md', 'harness/*',
            'AGENTS.md', 'PROMPTS.md', 'RULES.md', '.gitignore'
        ]
    elif scope == 'somente-testes':
        allowed_patterns = [
            'app/src/test/*', 'app/src/androidTest/*',
            '**/test/**', '**/androidTest/**', '*Test.kt', '*Test.java'
        ]

    # If no specific patterns defined for the scope, we allow everything for now
    # but we can add more strictness later if needed.
    if not allowed_patterns:
        return True, changed_files, []

    blocked_files = []
    passed_files = []

    for f in changed_files:
        is_allowed = False
        for pattern in allowed_patterns:
            if fnmatch.fnmatch(f, pattern) or f.startswith(pattern.replace('*', '')):
                is_allowed = True
                break

        if is_allowed:
            passed_files.append(f)
        else:
            blocked_files.append(f)

    return len(blocked_files) == 0, passed_files, blocked_files

def main():
    parser = argparse.ArgumentParser(description='Harness Scope Guardrail')
    parser.add_argument('--scope', required=True, choices=['somente-documentacao', 'somente-testes', 'refatoracao', 'feature', 'bugfix', 'infra'])
    parser.add_argument('--files', nargs='*', help='Explicit list of files to check (overrides git diff)')
    parser.add_argument('--commands', help='Commands executed during the task')
    parser.add_argument('--risks', help='Risks identified')

    args = parser.parse_args()

    files = args.files if args.files is not None else get_changed_files()

    if not files:
        # If no files changed and we are checking, it's technically passing but maybe a warning
        success = True
        passed = []
        blocked = []
    else:
        success, passed, blocked = validate_scope(args.scope, files)

    print("="*40)
    print("       HARNESS SCOPE REPORT")
    print("="*40)
    print(f"Scope: {args.scope}")
    print(f"Status: {'PASSOU' if success else 'BLOQUEADO'}")
    print("-"*40)
    print(f"Files Changed: {len(files)}")
    for f in files:
        status = "[OK]" if f in passed else "[BLOCKED]"
        print(f" {status} {f}")

    if args.commands:
        print("-"*40)
        print(f"Commands Executed: {args.commands}")

    if args.risks:
        print("-"*40)
        print(f"Risks: {args.risks}")

    print("="*40)
    print(f"FINAL DECISION: {'PASSOU' if success else 'BLOQUEADO'}")
    print("="*40)

    if not success:
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()
