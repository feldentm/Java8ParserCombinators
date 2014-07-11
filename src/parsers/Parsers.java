package parsers;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Contains some code and methods for the implementation of parser combinators.
 * 
 * In order to create a new parser, create a subclass of Parsers and assign
 * parse rules to fields of the subclass.
 * 
 * @author Timm Felden
 */
public class Parsers {

	/**
	 * Parse actions may throw parse exceptions to signal unrecoverable state.
	 * The usual behavior is to return a failure parse result.
	 * 
	 * @author Timm Felden
	 */
	public static class ParseException extends Exception {

		public ParseException(String msg) {
			super(msg);
		}

		public static void raisef(String msg, Object... args) throws ParseException {
			throw new ParseException(String.format(msg, args));
		}
	}

	/**
	 * A Token; can be anything between regular expressions on strings and
	 * binary chunks in binary files.
	 * 
	 * @author Timm Felden
	 */
	public interface Token {
		// has no interesting property in order to keep things flexible
	}

	/**
	 * Token to indicate the end of a token stream. There might be many of them.
	 * The end consist of an infinite amount of these tokens.
	 * 
	 * @author Timm Felden
	 */
	public final static class EndOfStream implements Token {
		EndOfStream instance = new EndOfStream();
	}

	/**
	 * The input of any parser.
	 * 
	 * @author Timm Felden
	 */
	public interface TokenStream {
		// the current token
		public Token token();

		// get next token
		public Token next();

		/**
		 * Cosume a token of argumet kind throwing a parse exception, if the
		 * kind did not match expectations.
		 * 
		 * @note The token is only consumed on match.
		 */
		public default void consume(Class<? extends Token> kind) throws ParseException {
			if (kind.isInstance(token()))
				next();
			else
				ParseException.raisef("%s did not match expected type %s", token(), kind);
		}
	}

	/**
	 * Wrapper for parse results. Can be either Success or Failure.
	 * 
	 * @author Timm Felden
	 *
	 * @param <R>
	 *            result type
	 */
	private abstract static class ParseResult<R> {
		public abstract R getOrElse(R r);

		public abstract boolean success();
	}

	/**
	 * Result of a successful parse.
	 */
	public final static class Success<R> extends ParseResult<R> {
		final R value;

		public Success(R value) {
			this.value = value;
		}

		public R get() {
			return value;
		}

		@Override
		public R getOrElse(R r) {
			return value;
		}

		@Override
		public boolean success() {
			return true;
		}

	}

	/**
	 * Unsuccessful parse result.
	 */
	public final static class Failure<R> extends ParseResult<R> {

		@Override
		public R getOrElse(R r) {
			return r;
		}

		@Override
		public boolean success() {
			return false;
		}

	}

	/**
	 * This interface provides implementation for abstract parse rules.
	 * 
	 * @author Timm Felden
	 *
	 * @param <R>
	 *            Result type of the parse result.
	 */
	public interface Parser<R> extends Function<TokenStream, ParseResult<R>> {

		public default <T> ParseResult<T> success(T r) {
			return new Success<T>(r);
		}

		public default <T> ParseResult<T> fail() {
			return new Failure<T>();
		}

		/**
		 * Syntactic sugar for application
		 */
		public default R get(TokenStream in) throws ParseException {
			ParseResult<R> r = apply(in);
			if (r instanceof Success)
				return ((Success<R>) r).get();

			throw new ParseException("Parse failed");
		}

		/**
		 * Chains two rules, executing both returning both results.
		 */
		public default <S> Parser<YouHaveToAddAnotherU<R, S>> then(final Parser<S> right) {
			return in -> {
				ParseResult<R> r = apply(in);
				if (r instanceof Success) {
					ParseResult<S> s = right.apply(in);
					if (s instanceof Success)
						return success(new YouHaveToAddAnotherU<R, S>(((Success<R>) r).get(), ((Success<S>) s).get()));
				}
				return fail();
			};
		}

		/**
		 * Chains two rules, executing both returning the left result only.
		 * 
		 * @note only the direct argument parser is ignored
		 */
		public default <S> Parser<R> thenIgnore(final Parser<S> right) {
			return in -> {
				ParseResult<R> r = apply(in);
				if (r instanceof Success) {
					ParseResult<S> s = right.apply(in);
					if (s instanceof Success)
						return r;
				}
				return fail();
			};
		}

		/**
		 * Chains two rules, executing both returning the right result only.
		 * 
		 * @note the accumulated parser is ignored in total; you may be required
		 *       to use parentheses
		 */
		public default <S> Parser<S> ignoreThen(final Parser<S> right) {
			return in -> {
				ParseResult<R> r = apply(in);
				if (r instanceof Success) {
					ParseResult<S> s = right.apply(in);
					if (s instanceof Success)
						return s;
				}
				return fail();
			};
		}

		/**
		 * Modify the result of a parser. Failures get mapped to failures
		 * without executing f.
		 */
		public default <T> Parser<T> map(Function<R, T> f) {
			return in -> {
				ParseResult<R> r = apply(in);
				if (r instanceof Success)
					return success(f.apply(((Success<R>) r).get()));

				return fail();
			};
		}
	}

	/**
	 * Chains several rules, executing first to last until one succeeds.
	 * 
	 * @note unchecked conversions happen, because the type system can not help
	 *       us
	 */
	@SuppressWarnings("unchecked")
	public static <S> Parser<S> choice(Parser<? extends S>... args) {
		return in -> {
			ParseResult<? extends S> r;
			for (Parser<? extends S> p : args) {
				r = p.apply(in);
				if (r instanceof Success)
					return (ParseResult<S>) r;
			}

			return (ParseResult<S>) fail;
		};
	}

	/**
	 * Helper class to improve the syntax of map. In fact this is just an
	 * instance of currying.
	 * 
	 * @author Timm Felden
	 *
	 * @param <R>
	 *            first intermediate result
	 * @param <S>
	 *            second intermediate result
	 */
	public static final class YouHaveToAddAnotherU<R, S> {
		R r;
		S s;

		public YouHaveToAddAnotherU(R r, S s) {
			this.r = r;
			this.s = s;
		}
	}

	/**
	 * Function type fixer for map and others.
	 */
	public static <R, S, T> Function<YouHaveToAddAnotherU<R, S>, T> u(Function<R, Function<S, T>> f) {
		return pack -> f.apply(pack.r).apply(pack.s);
	}

	@Deprecated
	public static <T> Parser<T> Lit(T value) {
		return in -> new Success<T>(value);
	}

	public static final Parser<?> fail = in -> new Failure<>();
}