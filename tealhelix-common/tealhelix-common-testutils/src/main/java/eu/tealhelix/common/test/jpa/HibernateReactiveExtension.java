package eu.tealhelix.common.test.jpa;

import static jakarta.persistence.Persistence.createEntityManagerFactory;

import java.util.Map;
import java.util.function.Supplier;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.reactive.mutiny.Mutiny.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Starts JPA and unwraps a Hibernate Reactive {@code Mutiny. SessionFactory}
 * to provide as argument to test methods.
 */
public class HibernateReactiveExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

	private final Supplier<String> dburlSupplier;
	private final String dbuser;
	private final String dbpass;
	private EntityManagerFactory entityManagerFactory;

	public HibernateReactiveExtension(Supplier<String> dburlSupplier, String dbuser, String dbpass) {
		this.dburlSupplier = dburlSupplier;
		this.dbuser = dbuser;
		this.dbpass = dbpass;
	}

	@Override
	public void beforeAll(ExtensionContext extensionContext) {
		var persistenceProps = Map.of(
				"jakarta.persistence.jdbc.url", dburlSupplier.get(),
				"jakarta.persistence.jdbc.user", dbuser,
				"jakarta.persistence.jdbc.password", dbpass
		);
		entityManagerFactory = createEntityManagerFactory("default-persistence-unit", persistenceProps);
	}

	@Override
	public void afterAll(ExtensionContext extensionContext) {
		if (entityManagerFactory != null) {
			entityManagerFactory.close();
			entityManagerFactory = null;
		}
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		Class<?> parameterType = parameterContext.getParameter().getType();
		return parameterType.equals(SessionFactory.class) || parameterType.equals(Statistics.class);
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
		Class<?> parameterType = parameterContext.getParameter().getType();
		if (parameterType.equals(SessionFactory.class)) {
			return getOrUnwrapSessionFactory(extensionContext);
		} else if (parameterType.equals(Statistics.class)) {
			return getOrUnwrapSessionFactory(extensionContext).getStatistics();
		} else {
			throw new IllegalArgumentException("cannot handle parameter of type " + parameterType.getName());
		}
	}

	private SessionFactory getOrUnwrapSessionFactory(@SuppressWarnings("unused") ExtensionContext extensionContext) {
		return entityManagerFactory.unwrap(SessionFactory.class);
	}
}
