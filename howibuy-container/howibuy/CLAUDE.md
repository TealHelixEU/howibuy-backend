## Vert.x threading rule

- A JAX-RS resource method that returns `Uni<T>` / `Multi<T>` runs on the event loop — DB calls are fine.
- A resource method that returns a plain synchronous type (e.g., a Qute `TemplateInstance`) is treated as blocking and
  dispatched on a worker thread. By default `@ServerRequestFilter` runs on the **same** thread as its resource — so a
  filter doing reactive Hibernate I/O on a blocking endpoint will fail. Use `@ServerRequestFilter(nonBlocking = true)`
  for any filter that touches the DB. `JwtAuthenticationFilter` already does this; see its Javadoc.
