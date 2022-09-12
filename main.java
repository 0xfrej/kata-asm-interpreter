import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;

class AssemblerInterpreter {
    private Map<String, Integer> labels = new java.util.HashMap<>();
    private Map<String, Integer> register = new java.util.HashMap<>();
    private List<Instruction> instructions = new java.util.ArrayList<>();
    private int PC = 0;
    private int lastCmp = 0;
    private Stack<Integer> callStack = new Stack<>();

    public static String interpret(final String input) {
        AssemblerInterpreter interpreter = new AssemblerInterpreter();
        interpreter.parse(input.split("\n"));

        return interpreter.run();
    }

    private void parse(final String[] lines) {
        List<Token> tokens = new java.util.ArrayList<>();
        for (String line : lines) {
            char[] chars = line.toCharArray();
            int i = 0;
            while (i < chars.length) {
                switch (chars[i]) {
                    case ';':
                        i = chars.length + 1;
                        break;
                    case ',':
                        break;
                    case '\'':
                        String literal = extractString(chars, i + 1);
                        i += literal.length() + 1;
                        tokens.add(new Token(Token.Type.STRING_LITERAL, literal));
                        break;
                    default:
                        char[] atom = extractAtom(chars, i).toCharArray();
                        i += atom.length;

                        if (atom.length > 0) {
                            if (atom[0] == '-' || Character.isDigit(atom[0])) {
                                tokens.add(new Token(Token.Type.NUMBER_LITERAL, new String(atom)));
                            }
                            else if (atom[atom.length - 1] == ':') {
                                tokens.add(new Token(Token.Type.LABEL, new String(atom).substring(0, atom.length - 1)));
                            }
                            else {
                                tokens.add(new Token(Token.Type.IDENTIFIER, new String(atom)));
                            }
                        }
                        break;
                }
                i++;
            }
            tokens.add(new Token(Token.Type.EOL, null));
        }

        int i = 0;
        int j = 0;
        Instruction instruction = new Instruction();
        while (i < tokens.size()) {
            Token token = tokens.get(i);
            if (token.type.equals(Token.Type.EOL)) {
                j = 0;
                i++;
                if (! instruction.isEmpty()) {
                    instructions.add(instruction);
                }
                instruction = new Instruction();
                continue;
            }
            else if (token.type.equals(Token.Type.LABEL)) {
                labels.put(token.content, instructions.size() - 1);
            }
            else if (token.type.equals(Token.Type.IDENTIFIER)) {
                if (j == 0) {
                    instruction.instruction = token.content;
                }
                else {
                    instruction.arguments.add(token);
                }
            }
            else if (token.type.equals(Token.Type.STRING_LITERAL) || token.type.equals(Token.Type.NUMBER_LITERAL)) {
                instruction.arguments.add(token);
            }
            i++;
            j++;
        }
    }

    private String extractAtom(char[] chars, int offset) {
        int i = offset;
        StringBuilder result = new StringBuilder();
        while(i < chars.length && ! Character.isWhitespace(chars[i])) {
            if (
                    chars[i] == ',' ||
                    chars[i] == ';'
            ) {
                break;
            }
            result.append(chars[i]);
            i++;
        }
        return result.toString();
    }

    private String extractString(char[] chars, int offset) {
        int i = offset;
        StringBuilder result = new StringBuilder();
        while(i < chars.length && chars[i] != '\'') {
            result.append(chars[i]);
            i++;
        }
        return result.toString();
    }

    static class Token {
        public Type type;
        public String content;

        public Token(Type type, String content) {
            this.type = type;
            this.content = content;
        }

        enum Type {
            IDENTIFIER, STRING_LITERAL, NUMBER_LITERAL, LABEL, EOL
        }
    }

    static class Instruction {
        String instruction;
        List<Token> arguments = new java.util.ArrayList<>();

        public Boolean isEmpty() {
            return instruction == null;
        }
    }

    static class Metadata {
        enum CmpFlag {
            EQUALS, GREATER, LESS
        }
    }

    private String run() {
        StringBuilder result = new StringBuilder();
        Instruction lastInstruction = null;
        while (PC < instructions.size()) {
            lastInstruction = instructions.get(PC);

            switch (lastInstruction.instruction) {
                case "mov": mov(lastInstruction.arguments); break;
                case "inc": inc(lastInstruction.arguments); break;
                case "dec": dec(lastInstruction.arguments); break;
                case "add": add(lastInstruction.arguments); break;
                case "sub": sub(lastInstruction.arguments); break;
                case "mul": mul(lastInstruction.arguments); break;
                case "div": div(lastInstruction.arguments); break;
                case "jmp": jmp(lastInstruction.arguments); break;
                case "cmp": cmp(lastInstruction.arguments); break;
                case "jne": jne(lastInstruction.arguments); break;
                case "je": je(lastInstruction.arguments); break;
                case "jge": jge(lastInstruction.arguments); break;
                case "jg": jg(lastInstruction.arguments); break;
                case "jle": jle(lastInstruction.arguments); break;
                case "jl": jl(lastInstruction.arguments); break;
                case "call": call(lastInstruction.arguments); break;
                case "ret": ret(); break;
                case "end": end(); break;
                case "msg": result.append(msg(lastInstruction.arguments)); break;
            }
            PC++;
        }

        if (lastInstruction != null && ! lastInstruction.instruction.equals("end")) {
            return null;
        }

        return result.toString();
    }

    private boolean getCmpFlag(Metadata.CmpFlag flag) {
        return ((lastCmp >> flag.ordinal()) & 1) != 0;
    }

    private void setCmpFlag(Metadata.CmpFlag flag) {
        lastCmp |= 1 << flag.ordinal();
    }

    @FunctionalInterface
    interface BinFunction<T, R> {
        public R apply(T a, T b);
    }

    private void uniOp(List<Token> arguments, Function<Integer, Integer> operator) {
        Token dst = arguments.get(0);

        if (dst.type.equals(Token.Type.IDENTIFIER)) {
            register.put(dst.content, operator.apply(
                    register.getOrDefault(dst.content, 0)
            ));
        }
    }

    private void binOp(List<Token> arguments, BinFunction<Integer, Integer> operator) {
        Token dst = arguments.get(0);
        Token src = arguments.get(1);

        if (src.type.equals(Token.Type.NUMBER_LITERAL)) {
            try {
                register.put(dst.content, operator.apply(
                        register.getOrDefault(dst.content, 0),
                        Integer.parseInt(src.content)
                ));
            } catch (Exception ignored) {}
        }
        else if (src.type.equals(Token.Type.IDENTIFIER)) {
            register.put(dst.content, operator.apply(
                    register.getOrDefault(dst.content, 0),
                    register.getOrDefault(src.content, 0)
            ));
        }
    }

    private void mov(List<Token> arguments) {
        Token dst = arguments.get(0);
        Token src = arguments.get(1);

        if (src.type.equals(Token.Type.NUMBER_LITERAL)) {
            try {
                register.put(dst.content, Integer.parseInt(src.content));
            } catch (Exception ignored) {}
        }
        else if (src.type.equals(Token.Type.IDENTIFIER)) {
            register.put(dst.content, register.getOrDefault(src.content, 0));
        }
    }

    private void inc(List<Token> arguments) {
        uniOp(arguments, (a) -> a + 1);
    }

    private void dec(List<Token> arguments) {
        uniOp(arguments, (a) -> a - 1);
    }

    private void add(List<Token> arguments) {
        binOp(arguments, Integer::sum);
    }

    private void sub(List<Token> arguments) {
        binOp(arguments, (a, b) -> a - b);
    }

    private void mul(List<Token> arguments) {
        binOp(arguments, (a, b) -> a * b);
    }

    private void div(List<Token> arguments) {
        binOp(arguments, (a, b) -> a / b);
    }

    private void jmp(List<Token> arguments) {
        Token dst = arguments.get(0);

        if (dst.type.equals(Token.Type.IDENTIFIER)) {
            PC = labels.get(dst.content);
        }
    }

    private void cmp(List<Token> arguments) {
        Token left = arguments.get(0);
        Token right = arguments.get(1);

        Integer lVal = null;
        Integer rVal = null;

        if (left.type.equals(Token.Type.NUMBER_LITERAL)) {
            try {
                lVal = Integer.parseInt(left.content);
            } catch (Exception ignored) {}
        }
        else if (left.type.equals(Token.Type.IDENTIFIER)) {
            lVal = register.getOrDefault(left.content, null);
        }

        if (right.type.equals(Token.Type.NUMBER_LITERAL)) {
            try {
                rVal = Integer.parseInt(right.content);
            } catch (Exception ignored) {}
        }
        else if (right.type.equals(Token.Type.IDENTIFIER)) {
            rVal = register.getOrDefault(right.content, null);
        }

        if (lVal != null && rVal != null) {
            if (lVal.equals(rVal)) {
                setCmpFlag(Metadata.CmpFlag.EQUALS);
            }
            if (lVal > rVal) {
                setCmpFlag(Metadata.CmpFlag.GREATER);
            }
            if (lVal < rVal) {
                setCmpFlag(Metadata.CmpFlag.LESS);
            }
        }
    }

    private void jmpIf(List<Token> arguments, boolean predicate) {
        if (predicate) {
            jmp(arguments);
            lastCmp = 0;
        }
    }

    private void jne(List<Token> arguments) {
        jmpIf(arguments, ! getCmpFlag(Metadata.CmpFlag.EQUALS));
    }

    private void je(List<Token> arguments) {
        jmpIf(arguments, getCmpFlag(Metadata.CmpFlag.EQUALS));
    }

    private void jge(List<Token> arguments) {
        jmpIf(arguments, getCmpFlag(Metadata.CmpFlag.GREATER) || getCmpFlag(Metadata.CmpFlag.EQUALS));
    }

    private void jg(List<Token> arguments) {
        jmpIf(arguments, getCmpFlag(Metadata.CmpFlag.GREATER));
    }

    private void jle(List<Token> arguments) {
        jmpIf(arguments, getCmpFlag(Metadata.CmpFlag.LESS) || getCmpFlag(Metadata.CmpFlag.EQUALS));
    }

    private void jl(List<Token> arguments) {
        jmpIf(arguments, getCmpFlag(Metadata.CmpFlag.LESS));
    }

    private void call(List<Token> arguments) {
        Token dst = arguments.get(0);

        if (dst.type.equals(Token.Type.IDENTIFIER)) {
            callStack.push(PC);
            PC = labels.get(dst.content);
        }
    }

    private void ret() {
        PC = callStack.pop();
    }

    private void end() {
        PC = instructions.size();
    }

    private String msg(List<Token> arguments) {
        StringBuilder result = new StringBuilder();
        for (Token arg : arguments) {
            if (arg.type.equals(Token.Type.IDENTIFIER)) {
                result.append(register.getOrDefault(arg.content, 0));
            }
            else if (arg.type.equals(Token.Type.STRING_LITERAL)) {
                result.append(arg.content);
            }
        }
        return result.toString();
    }
}
