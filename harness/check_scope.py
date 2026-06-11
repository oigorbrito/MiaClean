import argparse
import sys

def main():
    parser = argparse.ArgumentParser(description="Check scope")
    parser.add_argument("--type", help="Task type")
    parser.add_argument("--scope", help="Task scope")
    parser.add_argument("--base", help="Base SHA")
    parser.add_argument("--head", help="Head SHA")
    parser.add_argument("--allowed", help="Allowed files", action="append")
    parser.add_argument("--allow-critical", help="Allow critical changes", action="store_true")

    args = parser.parse_args()
    print("MOCK: check_scope.py running with args:", args)
    # Simulate PASS
    print("Resultado: PASSOU")

if __name__ == "__main__":
    main()
