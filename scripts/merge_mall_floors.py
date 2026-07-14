#!/usr/bin/env python3
"""
Merge Floor 3 graph into mall_graph.json (Floor 2).
- Node IDs on floor 3: +1000
- Shop IDs on floor 3: +1000
- Generates corridor edges on F3 via proximity (<=32px)
- Links floors where node coordinates align (<=3px), e.g. stairs/escalators
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
F2_PATH = ROOT / "app/src/main/assets/mall_graph.json"
F3_PATH = Path(r"c:\Users\Asus\Desktop\floor3.txt")
OUT_PATH = F2_PATH

NODE_OFFSET = 1000
SHOP_OFFSET = 1000
PROXIMITY_THRESHOLD = 32.0
INTER_FLOOR_THRESHOLD = 3.0
INTER_FLOOR_PENALTY_PX = 80.0  # documented; applied in app A*

F3_LOGOS = {
    "Concrete": "logos/concrete.jpeg",
    "Befit": None,
    "Levi's": "logos/levis",
    "Timberland": "logos/timberland.jpg",
    "Massimo Dutti": None,
}


def load_json(path: Path):
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def shift_node(node: dict) -> dict:
    out = dict(node)
    out["id"] = node["id"] + NODE_OFFSET
    out["floor"] = 3
    if node.get("shopId") is not None:
        out["shopId"] = node["shopId"] + SHOP_OFFSET
    name = node.get("shopName")
    if name and name in F3_LOGOS and not out.get("logo"):
        logo = F3_LOGOS[name]
        if logo:
            out["logo"] = logo
    return out


def proximity_edges(nodes: list[dict], threshold: float) -> set[tuple[int, int]]:
    edges = set()
    for i, a in enumerate(nodes):
        for b in nodes[i + 1 :]:
            d = math.hypot(a["x"] - b["x"], a["y"] - b["y"])
            if d <= threshold:
                edges.add(tuple(sorted((a["id"], b["id"]))))
    return edges


def inter_floor_edges(f2_nodes: list[dict], f3_nodes: list[dict], threshold: float) -> list[dict]:
    result = []
    for a in f2_nodes:
        if a.get("floor", 2) != 2:
            continue
        for b in f3_nodes:
            d = math.hypot(a["x"] - b["x"], a["y"] - b["y"])
            if d <= threshold:
                result.append({"from": a["id"], "to": b["id"]})
    return result


def main():
    f2 = load_json(F2_PATH)
    f3 = load_json(F3_PATH)

    f2_nodes = f2["nodes"]
    for n in f2_nodes:
        if "floor" not in n:
            n["floor"] = 2

    f3_nodes_raw = f3["nodes"]
    f3_nodes = [shift_node(n) for n in f3_nodes_raw]

    # F3 internal edges: explicit + proximity
    f3_edge_set = set()
    for e in f3["edges"]:
        f3_edge_set.add(tuple(sorted((e["from"] + NODE_OFFSET, e["to"] + NODE_OFFSET))))
    f3_edge_set |= proximity_edges(f3_nodes, PROXIMITY_THRESHOLD)

    f3_edges = [{"from": a, "to": b} for a, b in sorted(f3_edge_set)]

    # Inter-floor vertical links
    vertical = inter_floor_edges(f2_nodes, f3_nodes, INTER_FLOOR_THRESHOLD)

    merged = {
        "nodes": f2_nodes + f3_nodes,
        "edges": f2["edges"] + f3_edges + vertical,
    }

    with OUT_PATH.open("w", encoding="utf-8") as f:
        json.dump(merged, f, indent=2, ensure_ascii=False)
        f.write("\n")

    f2_ids = {n["id"] for n in f2_nodes}
    f3_ids = {n["id"] for n in f3_nodes}
    assert f2_ids.isdisjoint(f3_ids), "Node ID collision after offset"

    print(f"Floor 2 nodes: {len(f2_nodes)}, edges: {len(f2['edges'])}")
    print(f"Floor 3 nodes: {len(f3_nodes)}, internal edges: {len(f3_edges)}")
    print(f"Inter-floor edges: {len(vertical)}")
    print(f"Total nodes: {len(merged['nodes'])}, edges: {len(merged['edges'])}")
    print(f"Written: {OUT_PATH}")


if __name__ == "__main__":
    main()
