#!/usr/bin/env python3
"""Apply escalator/elevator transitions to mall_graph.json from mapping tables."""
import csv
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRAPH_PATH = ROOT / "app/src/main/assets/mall_graph.json"
F2_TABLE = Path(r"c:\Users\Asus\Desktop\floor2 eslcators and elvators.txt")
F3_TABLE = Path(r"c:\Users\Asus\Desktop\floor3 eslcators and elvators.txt")
NODE_OFFSET = 1000
COORD_MATCH_PX = 25.0


def read_table(path: Path) -> dict[int, dict]:
    rows = {}
    with path.open(encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            ee_id = int(row["Esclator_Elevator_id"].strip())
            rows[ee_id] = {
                "point_id": int(row["point_id"].strip()),
                "x": float(row["x_coordinates"].strip()),
                "y": float(row["y_coordinates"].strip()),
                "type": "elevator" if "elevator" in row["Type ( Escalator / Elevator )"].lower() else "escalator",
            }
    return rows


def resolve_node_id(node_by_id: dict, floor: int, point_id: int, x: float, y: float) -> int | None:
    if point_id in node_by_id and node_by_id[point_id].get("floor", 2) == floor:
        return point_id
    shifted = point_id + NODE_OFFSET
    if shifted in node_by_id and node_by_id[shifted].get("floor") == floor:
        return shifted
    candidates = [n for n in node_by_id.values() if n.get("floor", 2) == floor]
    if not candidates:
        return None
    best = min(candidates, key=lambda n: math.hypot(n["x"] - x, n["y"] - y))
    if math.hypot(best["x"] - x, best["y"] - y) <= COORD_MATCH_PX:
        return best["id"]
    return None


def main():
    f2 = read_table(F2_TABLE)
    f3 = read_table(F3_TABLE)
    common_ids = sorted(set(f2.keys()) & set(f3.keys()))

    graph = json.loads(GRAPH_PATH.read_text(encoding="utf-8"))
    node_by_id = {n["id"]: n for n in graph["nodes"]}

    pairs = []
    for ee_id in common_ids:
        f2_row = f2[ee_id]
        f3_row = f3[ee_id]
        f2_nid = resolve_node_id(node_by_id, 2, f2_row["point_id"], f2_row["x"], f2_row["y"])
        f3_nid = resolve_node_id(node_by_id, 3, f3_row["point_id"] + NODE_OFFSET, f3_row["x"], f3_row["y"])
        if f3_nid is None:
            f3_nid = resolve_node_id(node_by_id, 3, f3_row["point_id"], f3_row["x"], f3_row["y"])
        if f2_nid is None or f3_nid is None:
            print(f"WARN could not resolve transition {ee_id}: f2={f2_nid} f3={f3_nid}")
            continue
        pairs.append(
            {
                "ee_id": ee_id,
                "f2_node": f2_nid,
                "f3_node": f3_nid,
                "type": f2_row["type"],
            }
        )

    transition_node_ids = set()
    for p in pairs:
        transition_node_ids.add(p["f2_node"])
        transition_node_ids.add(p["f3_node"])

    for n in graph["nodes"]:
        for key in (
            "transitionType",
            "connectedFloor",
            "transitionNodeId",
            "escalatorElevatorId",
        ):
            n.pop(key, None)

    for p in pairs:
        n2 = node_by_id[p["f2_node"]]
        n3 = node_by_id[p["f3_node"]]
        n2["transitionType"] = p["type"]
        n2["connectedFloor"] = 3
        n2["transitionNodeId"] = p["f3_node"]
        n2["escalatorElevatorId"] = p["ee_id"]

        n3["transitionType"] = p["type"]
        n3["connectedFloor"] = 2
        n3["transitionNodeId"] = p["f2_node"]
        n3["escalatorElevatorId"] = p["ee_id"]

    transition_edges = set()
    for p in pairs:
        transition_edges.add(tuple(sorted((p["f2_node"], p["f3_node"]))))

    def floor_of(nid: int) -> int:
        return node_by_id[nid].get("floor", 2)

    kept_edges = []
    removed = 0
    for e in graph["edges"]:
        a, b = e["from"], e["to"]
        if a not in node_by_id or b not in node_by_id:
            kept_edges.append(e)
            continue
        if floor_of(a) != floor_of(b):
            key = tuple(sorted((a, b)))
            if key not in transition_edges:
                removed += 1
                continue
        kept_edges.append(e)

    existing = {tuple(sorted((e["from"], e["to"]))) for e in kept_edges}
    added = 0
    for a, b in transition_edges:
        if (a, b) not in existing:
            kept_edges.append({"from": a, "to": b})
            existing.add((a, b))
            added += 1

    graph["edges"] = kept_edges
    GRAPH_PATH.write_text(json.dumps(graph, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print(f"Transitions applied: {len(pairs)}")
    for p in pairs:
        print(f"  #{p['ee_id']} {p['type']}: F2 node {p['f2_node']} <-> F3 node {p['f3_node']}")
    print(f"Removed stray inter-floor edges: {removed}")
    print(f"Added transition edges: {added}")


if __name__ == "__main__":
    main()
