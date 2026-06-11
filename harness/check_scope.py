import argparse
import subprocess
import sys
import fnmatch
import os

def get_changed_files(base_sha, head_sha):
    try:
        result = subprocess.run(
            ['git', 'diff', '--name-status', base_sha, head_sha],
            capture_output=True,
            text=True,
            check=True
        )
        files = []
        for line in result.stdout.splitlines():
            if line:
                parts = line.split('\t')
                status = parts[0]
                filepath = parts[-1]
                files.append((status, filepath))
        return files
    except subprocess.CalledProcessError as e:
        print(f"Error running git diff: {e.stderr}")
        sys.exit(1)

def is_doc(path):
    return path.endswith('.md') or path.startswith('docs/') or path.startswith('harness/')

def is_test(path):
    return path.startswith('app/src/test/') or path.startswith('functions/src/__tests__/') or is_doc(path)

def is_infra(path):
    infra_patterns = [
        '*.gradle.kts',
        'gradle.properties',
        'gradle/*',
        'libs.versions.toml',
        '.github/*',
        'firebase.json',
        '.firebaserc',
        'firestore.*',
        'harness/*',
        '.gitignore',
        '*.bat',
        'gradlew'
    ]
    return any(fnmatch.fnmatch(path, p) for p in infra_patterns) or path.startswith('.github/') or path.startswith('gradle/')

def is_protected(path):
    protected_paths = [
        'app/src/main/java/com/miaclean/app/data/entitlement/',
        'functions/',
        'firebase.json'
    ]
    return any(path.startswith(p) for p in protected_paths)

def validate_scope(scope, changed_files):
    allowed_scopes = [
        'somente-documentacao',
        'somente-testes',
        'refatoracao',
        'feature',
        'bugfix',
        'infra'
    ]

    if scope not in allowed_scopes:
        return False, [f"Invalid scope: {scope}. Allowed: {', '.join(allowed_scopes)}"]

    blocked_files = []

    for status, path in changed_files:
        if scope == 'somente-documentacao':
            if not is_doc(path):
                blocked_files.append(path)
        elif scope == 'somente-testes':
            if not is_test(path):
                blocked_files.append(path)
        elif scope == 'infra':
            # infra can change almost anything related to infra, but we keep it simple
            # If it's not infra, we might want to warn or block, but usually infra tasks are broad.
            pass
        elif scope in ['feature', 'bugfix', 'refatoracao']:
            if is_protected(path):
                blocked_files.append(path)

    if blocked_files:
        return False, blocked_files
    return True, []

def main():
    parser = argparse.ArgumentParser(description='Check PR scope.')
    parser.add_argument('--scope', required=True, help='Declared scope of the PR')
    parser.add_argument('--base', required=True, help='Base SHA')
    parser.add_argument('--head', required=True, help='Head SHA')

    args = parser.parse_args()

    print(f"Escopo detectado: {args.scope}")
    print(f"Base SHA: {args.base}")
    print(f"Head SHA: {args.head}")

    changed_files = get_changed_files(args.base, args.head)

    print("\nArquivos alterados:")
    for status, path in changed_files:
        print(f"  {status}\t{path}")

    passed, blocked = validate_scope(args.scope, changed_files)

    if not passed:
        print("\nArquivos bloqueados:")
        for path in blocked:
            print(f"  {path}")
        print("\nDecisão: BLOQUEADO")
        sys.exit(1)
    else:
        print("\nArquivos bloqueados: Nenhum")
        print("\nDecisão: PASSOU")
        sys.exit(0)

if __name__ == '__main__':
    main()
