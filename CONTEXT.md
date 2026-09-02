# MallAR

Indoor AR navigation for shopping malls: the user scans a store logo to localize
themselves, picks a destination, and is guided there by an on-screen map and
AR arrows.

## Language

**Mall**:
One shopping centre the app can navigate. Exactly one Mall is *active* per app
session, chosen on the mall selection screen at launch. Today only the **City
Stars** Mall has navigation data; **City Centre Almaza** and **Mall of Egypt**
are offered in the picker but not yet navigable ("Coming soon").
_Avoid_: mall map, venue, location

**Mall session**:
The in-memory, per-launch record of which Mall the user picked, held in the
`MallSession` object. Not persisted — a new launch always re-asks.
_Avoid_: mall preference, saved mall

**Place**:
A single navigable point of interest inside a Mall — almost always a store, but
also landmarks and floor-transition points. Built from the mall graph's named
nodes.
_Avoid_: store (a Place is usually but not always a store), shop, POI

**Mall graph**:
The node-and-edge network for one Mall, per floor, that A* runs over to produce a
route. Loaded from the mall navigation map asset.
_Avoid_: navigation map (that is the on-disk file), floor plan (that is the
background image)

**Localization**:
Determining where the user physically is inside the Mall, by matching the camera
view of a store logo against the Mall's embeddings database.
_Avoid_: positioning, tracking (tracking is the ongoing pose update after
localization)
