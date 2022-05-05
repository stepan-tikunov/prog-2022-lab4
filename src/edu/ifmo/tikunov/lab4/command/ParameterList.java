package edu.ifmo.tikunov.lab4.command;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import edu.ifmo.tikunov.lab4.composite.CompositeParser;
import edu.ifmo.tikunov.lab4.validate.ConstraintValidator;

/**
 * Class that validates and parses parameters from string and user input.
 */
public class ParameterList {
    private final Parameter[] ctorParameters;
    private final Class<?>[] simpleTypes;
    private final String[] simpleDescriptions;
    private final Boolean[] isSimpleInitially;
    private final CommandParameter[] parameters;

    private Scanner in;

    /**
     * Returns whether the string is of type
     *
     * @param   param   string value of parameter
     * @param   type    type
     * @return  {@code true} if {@code param} is of {@code type}
     */
    private boolean paramIsOfType(String param, Class<?> type) {
        try {
            SimpleParser.parse(param, type);
            return true;
        } catch (BadParametersException e) {
            return false;
        }
    }

    private String getInput(String description) throws ExitSignal {
        System.out.print("Enter " + description + ": ");
        if (in.hasNextLine()) {
            return in.nextLine();
        } else {
            throw new ExitSignal("Command interrupted (^D)", 1);
        }
    }

    /**
     * Validates string against this parameter list.
     *
     * @param   params  string with parameters
     * @return  {@code true} if {@code params} matches this parameter list
     */
    public boolean validate(String params) {
        String[] separated = Stream.of(params.split("\\s+"))
                .filter(s -> !s.trim().equals(""))
                .collect(Collectors.toList())
                .toArray(new String[0]);

        try {
            return IntStream
                    .range(0, parameters.length)
                    .allMatch(i -> paramIsOfType(separated[i], simpleTypes[i]));
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    /**
     * @return parameter list with only simple parameters
     */
    public ParameterList onlySimple() {
        CommandParameter[] onlySimpleTypes = Stream.of(parameters)
                .filter(p -> SimpleParser.isSimple(p.type))
                .collect(Collectors.toList())
                .toArray(new CommandParameter[0]);
        return new ParameterList(onlySimpleTypes);
    }

    /**
     * @param params
     * @return Object[]
     * @throws BadParametersException
     */
    protected Object[] parse(String[] params) throws BadParametersException {
        Object[] result = new Object[parameters.length];
        Queue<String> paramsQueue = new LinkedList<>(Arrays.asList(params));

        for (int i = 0; i < parameters.length; ++i) {
            if (SimpleParser.isSimple(parameters[i].type)) {
                result[i] = SimpleParser.parse(paramsQueue.remove(), parameters[i].type);
            } else {
                try {
                    result[i] = CompositeParser.parse(paramsQueue, parameters[i].type);
                } catch (NoSuchElementException e) {
                    throw new BadParametersException(
                            "The specified string does not contain enough parameters for this command");
                }
            }
        }

        return result;
    }

    /**
     * Parses simple parameters from string and
     * additionaly asks user for composite parameters.
     *
     * @param   params  string with simple parameters
     * @return  parsed parameters
     * @throws  ExitSignal if user sends delimiter during input
     * @throws  BadParametersException if simple parameters don't match parameter list
     */
    public Object[] parseInteractive(String params) throws ExitSignal, BadParametersException {
        ParameterList simpleParamList = onlySimple();
        if (!simpleParamList.validate(params)) throw new BadParametersException("Bad parameters");
        String[] paramsSplit = Stream.of(params.split("\\s+"))
            .filter(s -> !s.trim().equals(""))
            .collect(Collectors.toList())
            .toArray(new String[0]);

        String[] allParams = new String[simpleTypes.length];

        for (int i = 0, j = 0; i < simpleTypes.length; ++i) {
            if (isSimpleInitially[i]) {
                allParams[i] = paramsSplit[j];
                ++j;
            } else {
                boolean validInput = false;
                while (!validInput) {
                    allParams[i] = getInput(simpleDescriptions[i]);
                    String message = ConstraintValidator.validateParameter(allParams[i], ctorParameters[i], simpleTypes[i]);
                    validInput = message.equals("");
                    if (!validInput)
                        System.err.print(message);
                }
            }
        }

        return parse(allParams);
    }

    /**
     * Parses parameters from string.
     *
     * @param   params  string with parameters
     * @return  parsed parameters
     * @throws  BadParametersException  if string doesn't match parameter list
     */
    public Object[] parse(String params) throws BadParametersException, ExitSignal {
        String[] paramsArray = Stream.of(params.split("\\s+"))
            .filter(s -> !s.trim().equals(""))
            .collect(Collectors.toList())
            .toArray(new String[0]);
        if (validate(params)) {
            return parse(paramsArray);
        } else {
            if (!onlySimple().validate(params)) {
                throw new BadParametersException("The specified string does not match the parameter list");
            }
            return parseInteractive(params);
        }
    }

    @Override
    public String toString() {
        return Stream.of(simpleDescriptions)
                .map(p -> "[" + p + "]")
                .collect(Collectors.joining(" "));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o instanceof ParameterList) {
            return Arrays.equals(parameters, ((ParameterList) o).parameters);
        }
        return false;
    }

    public ParameterList(CommandParameter... params) {
        in = new Scanner(System.in);
        parameters = params;

        List<Parameter> ctorParameters = Stream.of(params)
                .flatMap(p -> SimpleParser.isSimple(p.type) ? Stream.of((Parameter) null)
                        : CompositeParser.getCtorParameters(p.type).stream())
                .collect(Collectors.toList());

        List<Class<?>> simpleTypes = Stream.of(params)
                .flatMap(p -> SimpleParser.isSimple(p.type) ? Stream.of(p.type)
                        : CompositeParser.expandComposite(p.type).stream())
                .collect(Collectors.toList());

        List<String> simpleDescriptions = Stream.of(params)
                .flatMap(p -> SimpleParser.isSimple(p.type) ? Stream.of(p.name)
                        : CompositeParser.getDescriptions(p.type).stream())
                .collect(Collectors.toList());

        List<Boolean> isSimpleInitially = Stream.of(params)
                .flatMap(p -> SimpleParser.isSimple(p.type) ? Stream.of(true)
                        : CompositeParser.expandComposite(p.type).stream().map(any -> false))
                .collect(Collectors.toList());

        this.ctorParameters = ctorParameters.toArray(new Parameter[0]);
        this.simpleDescriptions = simpleDescriptions.toArray(new String[0]);
        this.simpleTypes = simpleTypes.toArray(new Class<?>[0]);
        this.isSimpleInitially = isSimpleInitially.toArray(new Boolean[0]);
    }
}
