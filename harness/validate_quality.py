import json
import math
import os

PHASH_THRESHOLD = 5
SEMANTIC_THRESHOLD = 0.92

def hamming_distance(h1, h2):
    if len(h1) != len(h2):
        return 999
    return sum(c1 != c2 for c1, c2 in zip(h1, h2))

def cosine_similarity(v1, v2):
    if not v1 or not v2 or len(v1) != len(v2):
        return 0.0
    dot = sum(a * b for a, b in zip(v1, v2))
    norm_a = math.sqrt(sum(a * a for a in v1))
    norm_b = math.sqrt(sum(b * b for b in v2))
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return dot / (norm_a * norm_b)

def build_groups(dataset):
    # Replicate ScanRepository.kt buildGroups logic

    # 1. Exact MD5
    exact_by_md5 = {}
    for item in dataset:
        md5 = item['md5']
        exact_by_md5.setdefault(md5, []).append(item['id'])

    exact_groups = [ids for ids in exact_by_md5.values() if len(ids) > 1]
    grouped_ids = set()
    for g in exact_groups:
        grouped_ids.update(g)

    # 2. Perceptual pHash (excluding exact)
    perceptual_candidates = [item for item in dataset if item['id'] not in grouped_ids]
    perceptual_groups = []

    if perceptual_candidates:
        # Disjoint Set logic for pHash
        parent = {item['id']: item['id'] for item in perceptual_candidates}
        def find(i):
            if parent[i] == i: return i
            parent[i] = find(parent[i])
            return parent[i]
        def union(i, j):
            root_i, root_j = find(i), find(j)
            if root_i != root_j: parent[root_i] = root_j

        for i in range(len(perceptual_candidates)):
            for j in range(i + 1, len(perceptual_candidates)):
                if hamming_distance(perceptual_candidates[i]['phash'], perceptual_candidates[j]['phash']) <= PHASH_THRESHOLD:
                    union(perceptual_candidates[i]['id'], perceptual_candidates[j]['id'])

        buckets = {}
        for item in perceptual_candidates:
            root = find(item['id'])
            buckets.setdefault(root, []).append(item['id'])

        perceptual_groups = [ids for ids in buckets.values() if len(ids) > 1]
        for g in perceptual_groups:
            grouped_ids.update(g)

    # 3. Semantic Embedding (excluding exact and perceptual)
    semantic_candidates = [item for item in dataset if item['id'] not in grouped_ids]
    semantic_groups = []
    visited = set()

    for i in range(len(semantic_candidates)):
        if semantic_candidates[i]['id'] in visited:
            continue

        bucket = [semantic_candidates[i]['id']]
        visited.add(semantic_candidates[i]['id'])

        for j in range(i + 1, len(semantic_candidates)):
            if semantic_candidates[j]['id'] in visited:
                continue

            if cosine_similarity(semantic_candidates[i]['embedding'], semantic_candidates[j]['embedding']) >= SEMANTIC_THRESHOLD:
                bucket.append(semantic_candidates[j]['id'])
                visited.add(semantic_candidates[j]['id'])

        if len(bucket) > 1:
            semantic_groups.append(bucket)
            grouped_ids.update(bucket)

    return {
        "EXACT_MD5": exact_groups,
        "PERCEPTUAL_PHASH": perceptual_groups,
        "SEMANTIC_EMBED": semantic_groups
    }

def evaluate(dataset, detected_groups):
    all_detected_pairs = set()
    for strategy, groups in detected_groups.items():
        for g in groups:
            for i in range(len(g)):
                for j in range(i + 1, len(g)):
                    all_detected_pairs.add(tuple(sorted((g[i], g[j]))))

    ground_truth_pairs = set()
    gt_groups = {}
    for item in dataset:
        gt = item['ground_truth_group']
        if gt is not None:
            gt_groups.setdefault(gt, []).append(item['id'])

    for g in gt_groups.values():
        for i in range(len(g)):
            for j in range(i + 1, len(g)):
                ground_truth_pairs.add(tuple(sorted((g[i], g[j]))))

    all_ids = [item['id'] for item in dataset]
    all_possible_pairs = set()
    for i in range(len(all_ids)):
        for j in range(i + 1, len(all_ids)):
            all_possible_pairs.add(tuple(sorted((all_ids[i], all_ids[j]))))

    tp = all_detected_pairs.intersection(ground_truth_pairs)
    fp = all_detected_pairs.difference(ground_truth_pairs)
    fn = ground_truth_pairs.difference(all_detected_pairs)
    tn = all_possible_pairs.difference(all_detected_pairs).difference(ground_truth_pairs)

    precision = len(tp) / len(all_detected_pairs) if all_detected_pairs else 1.0
    recall = len(tp) / len(ground_truth_pairs) if ground_truth_pairs else 1.0

    return {
        "tp": len(tp),
        "fp": len(fp),
        "fn": len(fn),
        "tn": len(tn),
        "precision": precision,
        "recall": recall,
        "incorrect_pairs": [list(p) for p in fp]
    }

def main():
    dataset_path = "harness/quality_dataset.json"
    with open(dataset_path, "r") as f:
        dataset = json.load(f)

    detected = build_groups(dataset)
    results = evaluate(dataset, detected)

    id_to_name = {item['id']: item['name'] for item in dataset}

    report = f"""# Scan Quality Report

## Configuration
- **pHash Hamming Distance Threshold**: {PHASH_THRESHOLD}
- **Semantic Cosine Similarity Threshold**: {SEMANTIC_THRESHOLD}

## Metrics Summary
- **Precision**: {results['precision']:.2%}
- **Recall**: {results['recall']:.2%}
- **False Positives (Pairs)**: {results['fp']}
- **False Negatives (Pairs)**: {results['fn']}

## Grouping Results
"""
    for strategy, groups in detected.items():
        report += f"### Strategy: {strategy}\n"
        if not groups:
            report += "No groups detected.\n"
        for i, g in enumerate(groups):
            names = [id_to_name[id] for id in g]
            report += f"- Group {i+1}: {', '.join(names)}\n"
        report += "\n"

    report += "## Error Analysis\n"
    if results['fp'] > 0:
        report += "### False Positives (Incorrectly Grouped)\n"
        for p in results['incorrect_pairs']:
            report += f"- {id_to_name[p[0]]} <-> {id_to_name[p[1]]}\n"
    else:
        report += "No false positives detected.\n"

    if results['fn'] > 0:
        report += "\n### False Negatives (Failed to Group)\n"
        # We don't explicitly list FN pairs here to keep it short, but could.
        report += f"Total FN pairs: {results['fn']}\n"

    report += """
## Suggestions
- If Precision is low (High FP): Increase SEMANTIC_THRESHOLD (e.g. 0.95) or decrease PHASH_THRESHOLD (e.g. 3).
- If Recall is low (High FN): Decrease SEMANTIC_THRESHOLD (e.g. 0.88) or increase PHASH_THRESHOLD (e.g. 8).
- Semantic embedding currently uses a greedy grouping; a more robust clustering (e.g. DBSCAN) might improve stability.
"""

    with open("docs/SCAN_QUALITY_REPORT.md", "w") as f:
        f.write(report)

    print("Report generated: docs/SCAN_QUALITY_REPORT.md")

if __name__ == "__main__":
    main()
