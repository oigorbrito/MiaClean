#!/usr/bin/env python3
import argparse
import sys
import os

# Ensure we can import check_scope from the same directory
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from check_scope import get_git_changes, validate_changes, match_path, SCOPE_RULES, CRITICAL_AREAS

CATEGORIES = {
    "documentação": [
        "docs/**",
        "*.md",
        "AGENTS.md",
        "PROMPTS.md",
        "RULES.md",
    ],
    "testes": [
        "app/src/test/**",
        "app/src/androidTest/**",
    ],
    "runtime app": [
        "app/src/main/**",
    ],
    "Firebase/backend": [
        "functions/**",
        "firebase.json",
        ".firebaserc",
        "firestore.rules",
        "firestore.indexes.json",
    ],
    "build/infra": [
        "*.gradle*",
        "gradle/**",
    ],
    "harness/CI": [
        "harness/**",
        ".github/workflows/**",
    ],
}

def get_category(path):
    for cat, patterns in CATEGORIES.items():
        for pattern in patterns:
            if match_path(path, pattern):
                return cat
    return "desconhecido"

def main():
    parser = argparse.ArgumentParser(description="Gera resumo de risco para PR.")
    parser.add_argument("--scope", required=True, help="Escopo declarado")
    parser.add_argument("--base", help="SHA base")
    parser.add_argument("--head", help="SHA head")
    parser.add_argument("--labels", help="Labels da PR (separadas por vírgula)")

    args = parser.parse_args()

    changes = get_git_changes(args.base, args.head)
    report, blocked = validate_changes(args.scope, changes, [], False)

    # Group changes by category
    grouped = {cat: [] for cat in CATEGORIES.keys()}
    grouped["desconhecido"] = []

    sensitive_touched = []
    deletions_detected = []

    for item in report:
        cat = get_category(item["path"])
        grouped[cat].append(item)

        if item["critical"]:
            sensitive_touched.append(item["path"])

        is_deletion = (item["status"] == "D")
        if is_deletion:
            deletions_detected.append(item["path"])

    # Determine recommendation
    recommendation = "seguro para review"
    if blocked:
        recommendation = "bloqueado"
    else:
        # Check if docs-only but has non-doc changes
        if args.scope == "somente-documentacao":
            non_doc_changes = [f for f in report if not any(match_path(f["path"], p) for p in SCOPE_RULES["somente-documentacao"])]
            if non_doc_changes:
                recommendation = "requer atenção"

        if sensitive_touched:
            recommendation = "requer atenção"

    # Generate Markdown
    print("<!-- mia-clean-risk-summary -->")
    print(f"### 🛡️ Resumo de Risco do Harness")
    print(f"| Atributo | Detalhe |")
    print(f"| :--- | :--- |")
    print(f"| **Escopo Declarado** | `{args.scope}` |")
    print(f"| **Labels Aplicadas** | `{args.labels if args.labels else 'nenhuma'}` |")
    print(f"| **Resultado Harness** | {'❌ BLOQUEADO' if blocked else '✅ PASSOU'} |")
    print(f"| **Recomendação** | **{recommendation.upper()}** |")
    print("\n#### 📂 Arquivos Alterados por Categoria")

    for cat in CATEGORIES.keys():
        files = grouped[cat]
        if files:
            print(f"- **{cat}**: {len(files)} arquivo(s)")
            for f in files[:5]:
                status_icon = "✅" if f["ok"] else "❌"
                print(f"  - {status_icon} `{f['path']}` ({f['status']})")
            if len(files) > 5:
                print(f"  - ... e mais {len(files) - 5}")

    if grouped["desconhecido"]:
        files = grouped["desconhecido"]
        print(f"- **desconhecido**: {len(files)} arquivo(s)")
        for f in files[:5]:
            status_icon = "✅" if f["ok"] else "❌"
            print(f"  - {status_icon} `{f['path']}` ({f['status']})")
        if len(files) > 5:
            print(f"  - ... e mais {len(files) - 5}")

    if sensitive_touched:
        print("\n#### ⚠️ Áreas Sensíveis Detectadas")
        for f in sensitive_touched:
            print(f"- `{f}`")

    if deletions_detected:
        print("\n#### 🗑️ Deleções Detectadas")
        for f in deletions_detected:
            print(f"- `{f}`")

if __name__ == "__main__":
    main()
