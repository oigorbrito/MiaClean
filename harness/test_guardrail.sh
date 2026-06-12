#!/bin/bash

echo "Testing Harness Guardrail: somente-documentacao"

# Case 1: Only documentation changes (Expected: PASS)
echo "Case 1: README.md and docs/PLAN.md"
python3 harness/guardrail.py --scope somente-documentacao --files README.md docs/PLAN.md
if [ $? -eq 0 ]; then
    echo "Result: PASS (Correct)"
else
    echo "Result: FAIL (Incorrect)"
    exit 1
fi

echo ""

# Case 2: Documentation + Kotlin change (Expected: BLOCKED)
echo "Case 2: README.md and app/src/main/java/MainActivity.kt"
python3 harness/guardrail.py --scope somente-documentacao --files README.md app/src/main/java/MainActivity.kt
if [ $? -eq 1 ]; then
    echo "Result: BLOCKED (Correct)"
else
    echo "Result: PASS (Incorrect)"
    exit 1
fi

echo ""

# Case 3: Documentation + Firebase config change (Expected: BLOCKED)
echo "Case 3: README.md and firebase.json"
python3 harness/guardrail.py --scope somente-documentacao --files README.md firebase.json
if [ $? -eq 1 ]; then
    echo "Result: BLOCKED (Correct)"
else
    echo "Result: PASS (Incorrect)"
    exit 1
fi

echo ""
echo "All guardrail tests passed!"
