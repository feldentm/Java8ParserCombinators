package test;

import parsers.Parsers;

public class Test extends Parsers {

	static Parser<String> check = choice(Lit("!").map(s -> {
		System.err.println("saw " + s);
		return s;
	}).thenIgnore(fail), Lit("Hello World!"));

	public static void main(String[] args) throws ParseException {
		System.out.println(check.get(null));

		Parser<YouHaveToAddAnotherU<String, String>> x = Lit("a").then(Lit("b"));

		Lit("y").then(x);
	}

}
