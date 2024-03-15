/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.brokercep.cep;

import gr.iccs.imu.ems.util.FunctionDefinition;
import gr.iccs.imu.ems.util.NetUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mariuszgromada.math.mxparser.*;
import org.mariuszgromada.math.mxparser.parsertokens.FunctionVariadic;
import org.mariuszgromada.math.mxparser.parsertokens.Token;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class MathUtil {
    static {
        License.iConfirmNonCommercialUse("EMS-"+ NetUtil.getIpAddress());
        mXparser.disableImpliedMultiplicationMode();
    }
    private static final Map<String, Function> functions = new HashMap<>();
    private static final Map<String, Constant> constants = new HashMap<>();

    // ------------------------------------------------------------------------

    public static void addFunctionDefinition(FunctionDefinition functionDef) {
        log.debug("MathUtil: Add new function definition: {}", functionDef);
        String argsStr = String.join(", ", functionDef.getArguments());
        //String defStr = String.format("%(%s) = %s", functionDef.getName(), argsStr, functionDef.getExpression());
        String defStr = functionDef.getName() + "(" + argsStr + ") = " + functionDef.getExpression();
        log.debug("MathUtil: definition-string: {}", defStr);
        Function func = new Function(defStr);
        functions.put(functionDef.getName(), func);
    }

    public static void clearFunctionDefinitions() {
        log.debug("MathUtil: Clear function definitions");
        functions.clear();
    }

    // ------------------------------------------------------------------------

    public static void setConstant(String constantName, double constantValue) {
        log.debug("MathUtil: Set constant: name={}, value={}", constantName, constantValue);
        Constant con = new Constant(constantName, constantValue);
        constants.put(constantName, con);
    }

    public static void setConstants(Map<String, Double> constantsMap) {
        log.debug("MathUtil: Add constants using map: {}", constantsMap);
        //constantsMap.entrySet().stream().forEach(c -> setConstant(c.getKey(), c.getValue()));
        constantsMap.forEach(MathUtil::setConstant);
    }

    public static void clearConstants() {
        log.debug("MathUtil: Clear constants");
        constants.clear();
    }

    // ------------------------------------------------------------------------

    public static @NonNull Set<String> getFormulaArguments(String formula) {
        log.debug("MathUtil: getFormulaArguments: formula={}", formula);
        if (StringUtils.isBlank(formula)) {
            log.debug("MathUtil: getFormulaArguments: Formula is null or empty");
            return Collections.emptySet();
        }

        // Create MathParser expression
        Expression e = new Expression(formula);
        //e.setVerboseMode();
        log.trace("MathUtil: getFormulaArguments: expression={}", e.getExpressionString());

        Set<String> argNames = extractArgNames(e);
        log.debug("MathUtil: getFormulaArguments: arguments={}", argNames);

        return argNames;
    }

    private static @NonNull Set<String> extractArgNames(Expression e) {

        List<Token> initTokens = extractFormulaTokens(e);

        Set<String> argNames = initTokens.stream()
                .filter(t -> t.tokenTypeId == Token.NOT_MATCHED)
                .filter(t -> "argument".equals(t.looksLike))
                .map(t -> t.tokenStr)
                .collect(Collectors
                        .toCollection(LinkedHashSet::new));
        log.debug("MathUtil: initial-token-names: {}", argNames);

        return argNames;
    }

    private static @NonNull List<Token> extractFormulaTokens(Expression e) {
        // Add constants
        e.addConstants(new ArrayList<>(constants.values()));

        // Add functions
        for (Function f : functions.values()) e.addFunctions(f);

        // Check syntax
        boolean lexSyntax = e.checkLexSyntax();
        boolean genSyntax = e.checkSyntax();
        if (log.isTraceEnabled()) {
            log.trace("MathUtil: lexSyntax={}, genSyntax: {}", lexSyntax, genSyntax);
            log.trace("MathUtil: syntax-status={}, error={}", e.getSyntaxStatus(), e.getErrorMessage());
        }

        // Get token names
        List<Token> initTokens = e.getCopyOfInitialTokens();
        if (log.isTraceEnabled()) {
            log.trace("MathUtil: initial-tokens={}", initTokens.stream()
                    .map(token -> Map.of(
                            "tokenId", token.tokenId,
                            "tokenType", token.tokenTypeId,
                            "tokenStr", token.tokenStr,
                            "tokenValue", token.tokenValue,
                            "looksLike", token.looksLike,
                            "keyWord", token.keyWord,
                            "tokenLevel", token.tokenLevel,
                            "is-identifier", token.isIdentifier()
                            )
                    ).toList()
            );
            mXparser.consolePrintTokens(initTokens);
        }

        return initTokens;
    }

    // ------------------------------------------------------------------------

    public static boolean containsAggregator(String formula) {
        log.debug("MathUtil: containsAggregator: formula={}", formula);
        if (StringUtils.isBlank(formula)) {
            log.debug("MathUtil: containsAggregator: Formula is null or empty");
            return false;
        }

        // Create MathParser expression
        Expression e = new Expression(formula);
        //e.setVerboseMode();
        log.trace("MathUtil: containsAggregator: expression={}", e.getExpressionString());

        // Get formula tokens
        List<Token> initTokens = extractFormulaTokens(e);

        // Select 'function' names from tokens
        List<String> names = initTokens.stream()
                .filter(t -> t.tokenTypeId == FunctionVariadic.TYPE_ID)
                .map(t -> t.tokenStr)
                .collect(Collectors.toList());
        log.trace("MathUtil: containsAggregator: formula-aggregator-functions: {}", names);

        // Check if aggregators exist
        boolean containsAgg = names.size() > 0;
        if (containsAgg)
            log.debug("MathUtil: containsAggregator: Formula contains aggregators: aggregators={}, formula={}", names, formula);
        else
            log.debug("MathUtil: containsAggregator: Formula does not contain aggregators: {}", formula);
        return containsAgg;
    }

    // ------------------------------------------------------------------------

    protected final static String[] aggregatorNames = {"iff", "min", "max", "ConFrac", "ConPol", "gcd", "lcm", "add", "multi", "mean", "var", "std", "rList"};

    public static boolean containsAggregatorRegexp(String formula) {
        log.debug("MathUtil: containsAggregatorRegexp: formula={}", formula);
        if (StringUtils.isBlank(formula)) {
            log.debug("MathUtil: containsAggregatorRegexp: Formula is null or empty");
            return false;
        }
        formula = " " + formula;
        for (int i = 0; i < aggregatorNames.length; i++) {
            log.trace("MathUtil: containsAggregatorRegexp: checking aggregator: aggregator={}, formula={}", aggregatorNames[i], formula);
            if (checkPattern(formula, aggregatorNames[i])) {
                log.debug("MathUtil: containsAggregatorRegexp: Formula contains aggregators: aggregator={}, formula={}", aggregatorNames[i], formula);
                return true;
            }
        }
        log.debug("MathUtil: containsAggregatorRegexp: Formula does not contain aggregators: formula={}", formula);
        return false;
    }

    protected static boolean checkPattern(String formula, String aggregatorName) {
        int flags = Pattern.CASE_INSENSITIVE;
        Pattern pat = Pattern.compile("[^a-zA-Z]%s[^a-zA-Z]".formatted(aggregatorName), flags);
        return pat.matcher(formula).find();
    }

    // ------------------------------------------------------------------------

    public static double evalAgg(String formula, Map<String, List<Double>> argsMap) {
        log.debug("MathUtil: evalAgg: input: formula={}, arg-map={}", formula, argsMap);
        int iter = 0;
        for (Map.Entry<String, List<Double>> arg : argsMap.entrySet()) {
            log.debug("MathUtil: evalAgg: iteration #{}: arg={}", iter, arg);
            String argName = arg.getKey();
            List<Double> argValue = arg.getValue();
            log.debug("MathUtil: evalAgg: iteration #{}: arg-name={}, arg-value={}", iter, argName, argValue);
            String valStr = argValue.stream().map(value -> value.toString()).collect(Collectors.joining(", "));
            log.debug("MathUtil: evalAgg: iteration #{}: arg-name={}, arg-value-str={}", iter, argName, valStr);

            formula = formula.replaceAll(argName, valStr);
            iter++;
        }
        log.debug("MathUtil: evalAgg: formula-to-evaluate: {}", formula);

        return eval(formula, new java.util.HashMap<>());
    }

    public static double eval(String formula, Map<String, Double> argsMap) {
        // Create MathParser expression
        Expression e = new Expression(formula);
        //e.setVerboseMode();
        log.debug("MathUtil: formula={}", e.getExpressionString());

        // Get argument names
        Set<String> argNames = extractArgNames(e);

        // Define expression arguments with user provided values
        //e.removeAllArguments();
        for (String argName : argNames) {
            try {
                log.debug("MathUtil: Defining Arg: {}", argName);
                double argValue = argsMap.get(argName);
                e.defineArgument(argName, argValue);
                log.debug("MathUtil: Arg: {} = {}", argName, argValue);
            } catch (Exception ex) {
                log.error("MathUtil: Defining Arg: EXCEPTION: arg-name={}, args-map={}", argName, argsMap);
                throw ex;
            }
        }
        boolean genSyntax = e.checkSyntax();
        if (!genSyntax)
            throw new IllegalArgumentException("Syntax error in expression: " + e.getErrorMessage());

        // Calculate result
        double result = e.calculate();
        log.debug("MathUtil: Result={}, computing-time={}, error={}", result, e.getComputingTime(), e.getErrorMessage());

        if (Double.isInfinite(result) || Double.isNaN(result)) {
            log.warn("MathUtil: ------------------------------------------------------------------------");
            log.warn("MathUtil: Result is NaN or Infinite: result={}", result);
            log.warn("MathUtil: Context:          formula: {}", formula);
            log.warn("MathUtil: Context:         args-map: {}", argsMap);
            log.warn("MathUtil: Context:        constants: {}", constants.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, x->x.getValue().getConstantValue()
            )));
            log.warn("MathUtil: Context:        functions: {}", functions.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, x->x.getValue().getFunctionExpressionString()
            )));
            log.warn("MathUtil: ------------------------------------------------------------------------");
            throw new IllegalStateException("MathUtil.eval result is NaN or Infinite: "+result);
        }

        return result;
    }
}
