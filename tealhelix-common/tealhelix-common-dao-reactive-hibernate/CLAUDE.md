## The Hibernate-Reactive / Vert.x threading rule

**Any code path that reaches `sessionFactory.withSession(...)` / `withTransaction(...)` must execute on a Vert.x
event-loop thread.** Hibernate Reactive enforces this with the error:

> This method should exclusively be invoked from a Vert.x EventLoop thread

Practical consequences:

- A JAX-RS resource method that returns `Uni<T>` / `Multi<T>` runs on the event loop — DB calls are fine.
- A resource method that returns a plain synchronous type (e.g., a Qute `TemplateInstance`) is treated as blocking and
  dispatched on a worker thread. By default `@ServerRequestFilter` runs on the **same** thread as its resource — so a
  filter doing reactive Hibernate I/O on a blocking endpoint will fail. Use `@ServerRequestFilter(nonBlocking = true)`
  for any filter that touches the DB. `JwtAuthenticationFilter` already does this; see its Javadoc.
- For tests using Mutiny outside a request scope, drive subscriptions with `UniAssertSubscriber` or
  `await().atMost(...)` from the main thread — fine, because Hibernate Reactive isn't involved unless you wire it in
  (`MockReactivePersistenceContextFactory` exists for that).

When you see the EventLoop-thread error, the question is always "which thread is the chain being subscribed on, and why
isn't it the event loop?" — not "how do I switch threads to get around the check."
