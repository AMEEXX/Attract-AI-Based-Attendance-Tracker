# LLD Template

Copy this structure into a numbered LLD before it is marked Drafting. The depth of each section varies, but every section must be considered.

1. Purpose and responsibility
2. Scope and explicit non-scope
3. Requirements traceability to 00 Main SDD
4. Dependencies and assumptions
5. Inputs and outputs
6. Components, classes, and interfaces
7. Data models and persistence interaction
8. Public APIs / method contracts
9. Internal algorithms and invariants
10. State machine and valid transitions
11. Transactions, idempotency, and consistency
12. Coroutine, threading, and cancellation model
13. Lifecycle and configuration-change behavior
14. Error taxonomy, user messaging, and recovery
15. Security and privacy considerations
16. Edge cases and abuse cases
17. Logging, metrics, and observability
18. Unit-test specification
19. Integration-test specification
20. Performance and compatibility acceptance criteria
21. Definition of done and approval record

Every LLD must state what it will **not** do. This is essential for retaining Attract’s minimal MVP scope.

