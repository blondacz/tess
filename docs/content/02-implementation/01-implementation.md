Implementation
--

This is a small in-memory event-sourced system written in Scala. It wires together actors, a coordinator with a lightweight unit-of-work staging area, an event store, and a dispatcher used for post-commit publishing.

### Core types
- `Message` and `Event`: input/output signals for the system. `EventMessage` wraps events when reprocessing.
- `Actor`: domain object that can produce new events from messages (`receive`) and mutate itself from events (`update`). Each actor has a typed `Id`.
- `ActorFactory`: routes messages to actor IDs, builds new actors from creation events, and exposes the actor class for lookup.
- `ActorUnitOfWork`: a batch of reactions for a single actor (events, commands, notifications), with the actor version, starting reaction rank, and a computed ending rank.

### Persistence and coordination
- `EventStore`: minimal trait with `store`, `load`, and `lastReactionRank`. Current implementation is `InMemoryEventStore`, which just holds data in a map keyed by `ActorKey`.
- `Dispatcher`: receives unit-of-work batches before commit, and is told when a commit succeeds. `MemorizingDispatcher` buffers dispatched UOWs, supports `replay(fromRank)`, and trims on rollback.
- `SimpleCoordinator`:
  - Keeps an in-memory map of staged actors and their UOWs.
  - `start()` grabs the next reaction rank (last rank + 1, defaulting to 1).
  - `store(uow, actor)` stages the UOW, updates `reactionRank`, and calls `dispatcher.dispatch(uow)` immediately.
  - `commit()` flushes all staged UOWs to the `EventStore`, clears the stage, and calls `dispatcher.commit(lastReactionRank)`.
  - `rollback()` clears staged UOWs and drops the transient `reactionRank`.
  - `load()` reads from the staged map first, otherwise from the `EventStore`, and rehydrates via `Rehydrator`.

### Message handling flow
- `MessageHandler` wraps an `ActorFactory` and the `Coordinator`.
  - On `handle(msg)`, it computes the current reaction rank (last + 1).
  - For each routed actor ID:
    - If no actor exists, it creates one using the factory, produces initial events, updates the actor with any additional events, and stages a UOW starting at the current rank. The rank is advanced by the number of produced events.
    - If the actor exists (rehydrated), it produces events via `receive`, updates the actor, stages a UOW, and advances the rank by the number of events.
- `EventSourcedSystem.process(msg)`:
  - Calls `coordinator.start()` to capture the starting rank.
  - Recursively processes produced UOWs into messages via `EventMessage` (`split`), driving any downstream handlers.
  - On success, commits and returns the dispatcher replay from the starting rank; on failure, rolls back.

### Rehydration
- `Rehydrator` can build a new actor from a sequence of UOWs (`rehydrateNewActor`) and apply events to an existing actor (`updateActor`).

### Example domain
- `Customer` and `Basket` demonstrate a simple causal chain:
  - A single command `AddItemsForCustomer` is used for both create and update:
    - `CustomerFactory` turns the command into `CustomerCreated` on the first sight of an ID; the actor’s `receive` then emits `CustomerUpdated` (the same command path drives both creation and later updates).
  - `Basket` listens to `CustomerUpdated` events (wrapped in `EventMessage`), creating itself with `BasketCreated` and then appending items via `BasketUpdated`.
  - `ListBasket` is a read-style command that produces `BasketListed` containing the current items without mutating state.

### Current semantics and limits
- Persistence is in-memory only; there is no durability.
- `dispatcher.dispatch` is called before commit; rollback clears staged UOWs and trims dispatcher state, but there is no undo of persisted data (because the store is in-memory).
- There is no Raft/log integration yet; event ranks are local to the coordinator’s view of the store.

### Message processing interaction

- Mermaid diagram: `docs/content/02-implementation/message-processing.mmd`
- PlantUML diagram: `docs/content/02-implementation/message-processing.puml`
