package babycino;

import java.io.*;
import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

class BabycinoParserWrapper
{
	String getLiteralName(int tokenType) {
		return MiniJavaLexer.VOCABULARY.getLiteralName(tokenType);
	}
	
    // Uses the generated MiniJavaLexer to transform an arbitrary string input into a series of tokens
    List<Token> tokeniseInput(String input)
    {
        ByteArrayInputStream byteStream = null;
        CharStream charStream = null;
        MiniJavaLexer lexer = null;
        CommonTokenStream tokenStream = null;
        try {
            byteStream = new ByteArrayInputStream(input.getBytes());
            charStream = CharStreams.fromStream(byteStream);
            lexer = new MiniJavaLexer(charStream);
            tokenStream = new CommonTokenStream(lexer);
            tokenStream.fill();
        } catch (Exception e) { }
        return tokenStream.getTokens();
    }
}