package eu.tealhelix.common.utils;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Functions.Function3;
import io.smallrye.mutiny.tuples.Functions.Function4;
import io.smallrye.mutiny.tuples.Functions.Function5;
import io.smallrye.mutiny.tuples.Functions.Function6;
import io.smallrye.mutiny.tuples.Functions.Function7;
import io.smallrye.mutiny.tuples.Functions.Function8;
import io.smallrye.mutiny.tuples.Functions.Function9;

/**
 * The {@code UniComprehensions} try to make it easier to work with chains of {@code Uni.flatMap} operations, especially
 * in the case where an operation needs the results of more than one operation preceding it. The name is inspired from
 * <a href="https://docs.scala-lang.org/tour/for-comprehensions.html">Scala's for comprehensions</a>.
 * <p>
 * There are two flavors of each function: {@code forc} applies a chain of {@code Uni.flatMap} operations, while
 * {@code forcm} applies a chain of {@code Uni.flatMap} operations, followed by a single {@code Uni.map} operation.
 * </p>
 * <p>
 * To avoid chaos, the N-th {@code Uni.flatMap} operation may depend on the results of the 1...(N-1) previous operations.
 * It is not allowed to skip a result, even if it is unnecessary for the operation.
 * </p>
 */
public interface UniComprehensions {

	//----------- 2 steps + optional mapper -----------

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R>> mapper1
	) {
		return init.flatMap(mapper1);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R> Uni<R> forc(
			Uni<R1> init,
			Supplier<Uni<? extends R>> mapper1
	) {
		return init.chain(mapper1);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, ? extends R> finalMapper
	) {
		return init.flatMap(mapper1).map(finalMapper);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, ? extends R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).map(r2 -> finalMapper.apply(r1, r2)));
	}

	//----------- 3 steps + optional mapper -----------

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R>> mapper2
	) {
		return init.flatMap(mapper1).flatMap(mapper2);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R>> mapper2
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2)));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, ? extends R> finalMapper
	) {
		return init.flatMap(mapper1).flatMap(mapper2).map(finalMapper);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, ? extends R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).map(r3 -> finalMapper.apply(r1, r2, r3))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, ? extends R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2))).map(finalMapper);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<? super R2, ? super R3, ? extends R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).map(r3 -> finalMapper.apply(r2, r3))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, ? extends R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).map(r3 -> finalMapper.apply(r1, r2, r3))));
	}

	//----------- 4 steps + optional mapper -----------

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(mapper1).flatMap(mapper2).flatMap(mapper3);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2))).flatMap(mapper3);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			BiFunction<? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(mapper1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r2, r3)));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r1, r2, r3))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			BiFunction<? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r2, r3))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R>> mapper3
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<R1, R2, R3, R4, R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3).map(r4 -> finalMapper.apply(r1, r2, r3, r4)))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R4>> mapper3,
			Function4<R1, R2, R3, R4, R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r3).map(r4 -> finalMapper.apply(r1, r2, r3, r4)))));
	}

	//----------- 5 steps + optional mapper -----------

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R4, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R4>> mapper3,
			Function<? super R4, Uni<? extends R>> mapper4
	) {
		return init.flatMap(mapper1).flatMap(mapper2).flatMap(mapper3).flatMap(mapper4);
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R4, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R4>> mapper3,
			BiFunction<? super R3, ? super R4, Uni<? extends R>> mapper4
	) {
		return init.flatMap(mapper1).flatMap(mapper2).flatMap(r3 -> mapper3.apply(r3).flatMap(r4 -> mapper4.apply(r3, r4)));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R4>> mapper3,
			Function<? super R4, Uni<? extends R5>> mapper4,
			Function5<R1, R2, R3, R4, R5, R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r3).flatMap(r4 -> mapper4.apply(r4).map(r5 -> finalMapper.apply(r1, r2, r3, r4, r5))))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function5<R1, R2, R3, R4, R5, R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3).flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).map(r5 -> finalMapper.apply(r1, r2, r3, r4, r5))))));
	}

	//----------- 6 steps + optional mapper -----------

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R4, R5, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R4>> mapper3,
			Function<? super R4, Uni<? extends R5>> mapper4,
			Function<? super R5, Uni<? extends R>> mapper5
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r3).flatMap(r4 -> mapper4.apply(r4).flatMap(mapper5::apply)))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R4, R5, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function<? super R5, Uni<? extends R>> mapper5
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r1, r2, r3).flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(mapper5)))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R4, R5, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function3<? super R3, ? super R4, ? super R5, Uni<? extends R>> mapper5
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r1, r2, r3).flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(r5 -> mapper5.apply(r3, r4, r5))))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}.
	 */
	static <R1, R2, R3, R4, R5, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			Function<? super R2, Uni<? extends R3>> mapper2,
			Function<? super R3, Uni<? extends R4>> mapper3,
			Function<? super R4, Uni<? extends R5>> mapper4,
			BiFunction<? super R4, ? super R5, Uni<? extends R>> mapper5
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r2).flatMap(r3 -> mapper3.apply(r3).flatMap(r4 -> mapper4.apply(r4).flatMap(r5 -> mapper5.apply(r4, r5))))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function5<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, Uni<? extends R>> mapper5
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3)
				.flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(r5 -> mapper5.apply(r1, r2, r3, r4, r5))))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R6, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function5<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, Uni<? extends R6>> mapper5,
			Function6<R1, R2, R3, R4, R5, R6, R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3)
				.flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(r5 -> mapper5.apply(r1, r2, r3, r4, r5)
						.map(r6 -> finalMapper.apply(r1, r2, r3, r4, r5, r6)))))));
	}

	//----------- 7 steps + optional mapper -----------

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R6, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function5<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, Uni<? extends R6>> mapper5,
			Function6<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, Uni<? extends R>> mapper6
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3)
				.flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(r5 -> mapper5.apply(r1, r2, r3, r4, r5)
						.flatMap(r6 -> mapper6.apply(r1, r2, r3, r4, r5, r6)))))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R6, R7, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function5<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, Uni<? extends R6>> mapper5,
			Function6<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, Uni<? extends R7>> mapper6,
			Function7<R1, R2, R3, R4, R5, R6, R7, R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3)
				.flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(r5 -> mapper5.apply(r1, r2, r3, r4, r5)
						.flatMap(r6 -> mapper6.apply(r1, r2, r3, r4, r5, r6)
								.map(r7 -> finalMapper.apply(r1, r2, r3, r4, r5, r6, r7))))))));
	}

	//----------- 8 steps + optional mapper -----------

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R6, R7, R> Uni<R> forc(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function5<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, Uni<? extends R6>> mapper5,
			Function6<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, Uni<? extends R7>> mapper6,
			Function7<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, ? super R7, Uni<? extends R>> mapper7
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3)
				.flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(r5 -> mapper5.apply(r1, r2, r3, r4, r5)
						.flatMap(r6 -> mapper6.apply(r1, r2, r3, r4, r5, r6)
								.flatMap(r7 -> mapper7.apply(r1, r2, r3, r4, r5, r6, r7))))))));
	}

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R6, R7, R8, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function5<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, Uni<? extends R6>> mapper5,
			Function6<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, Uni<? extends R7>> mapper6,
			Function7<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, ? super R7, Uni<? extends R8>> mapper7,
			Function8<R1, R2, R3, R4, R5, R6, R7, R8, R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3)
				.flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(r5 -> mapper5.apply(r1, r2, r3, r4, r5)
						.flatMap(r6 -> mapper6.apply(r1, r2, r3, r4, r5, r6)
								.flatMap(r7 -> mapper7.apply(r1, r2, r3, r4, r5, r6, r7)
										.map(r8 -> finalMapper.apply(r1, r2, r3, r4, r5, r6, r7, r8)))))))));
	}

	//----------- 9 steps + optional mapper -----------

	/**
	 * Apply a chain of {@code Uni.flatMap} operations to the first argument {@code Uni}, followed by a single {@code Uni.map} operation.
	 */
	static <R1, R2, R3, R4, R5, R6, R7, R8, R9, R> Uni<R> forcm(
			Uni<R1> init,
			Function<? super R1, Uni<? extends R2>> mapper1,
			BiFunction<? super R1, ? super R2, Uni<? extends R3>> mapper2,
			Function3<? super R1, ? super R2, ? super R3, Uni<? extends R4>> mapper3,
			Function4<? super R1, ? super R2, ? super R3, ? super R4, Uni<? extends R5>> mapper4,
			Function5<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, Uni<? extends R6>> mapper5,
			Function6<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, Uni<? extends R7>> mapper6,
			Function7<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, ? super R7, Uni<? extends R8>> mapper7,
			Function8<? super R1, ? super R2, ? super R3, ? super R4, ? super R5, ? super R6, ? super R7, ? super R8, Uni<? extends R9>> mapper8,
			Function9<R1, R2, R3, R4, R5, R6, R7, R8, R9, R> finalMapper
	) {
		return init.flatMap(r1 -> mapper1.apply(r1).flatMap(r2 -> mapper2.apply(r1, r2).flatMap(r3 -> mapper3.apply(r1, r2, r3)
				.flatMap(r4 -> mapper4.apply(r1, r2, r3, r4).flatMap(r5 -> mapper5.apply(r1, r2, r3, r4, r5)
						.flatMap(r6 -> mapper6.apply(r1, r2, r3, r4, r5, r6)
								.flatMap(r7 -> mapper7.apply(r1, r2, r3, r4, r5, r6, r7)
										.flatMap(r8 -> mapper8.apply(r1, r2, r3, r4, r5, r6, r7, r8)
												.map(r9 -> finalMapper.apply(r1, r2, r3, r4, r5, r6, r7, r8, r9))))))))));
	}
}
