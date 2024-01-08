/*
 * Copyright (C) 2017-2023 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0, unless
 * Esper library is used, in which case it is subject to the terms of General Public License v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

package gr.iccs.imu.ems.baguette.client.cluster;

import io.atomix.cluster.Member;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.parsertokens.Token;

import java.util.List;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

@Data
@Builder
public class MemberScoreFunction implements Function<Member, Double> {
    private final String formula;
    private final double defaultScore;
    private final Properties argumentDefaults;
    private boolean throwExceptions;

    public MemberScoreFunction(String formula) {
        this(formula, -1, new Properties(), false);
    }

    public MemberScoreFunction(String formula, double defaultScore) {
        this(formula, defaultScore, new Properties(), false);
    }

    public MemberScoreFunction(String formula, Properties defaults) {
        this(formula, -1, defaults, false);
    }

    public MemberScoreFunction(String formula, double defaultScore, Properties defaults, boolean throwExceptions) {
        Expression e = new Expression(formula);
        //e.setVerboseMode();
        if (!e.checkLexSyntax())
            throw new IllegalArgumentException("Lexical syntax error in expression: " + e.getErrorMessage());
        this.formula = formula;
        this.defaultScore = defaultScore;
        this.argumentDefaults = defaults;
        this.throwExceptions = throwExceptions;
    }

    @Override
    public Double apply(Member member) {
        return evaluateExpression(formula, member.properties());
    }

    protected List<String> getExpressionArguments(Expression e) {
        // Get argument names
        boolean lexSyntax = e.checkLexSyntax();
        boolean genSyntax = e.checkSyntax();

        List<Token> initTokens = e.getCopyOfInitialTokens();
        List<String> argNames = initTokens.stream()
                .filter(t -> t.tokenTypeId == Token.NOT_MATCHED)
                .filter(t -> "argument".equals(t.looksLike))
                .map(t -> t.tokenStr)
                .collect(Collectors.toList());

        return argNames;
    }

    public double evaluateExpression(String formula, Properties args) {
        try {
            if (StringUtils.isBlank(formula)) {
                throw new IllegalArgumentException("Formula is empty or null");
            }

            // Create MathParser expression
            Expression e = new Expression(formula);
            //e.setVerboseMode();

            // Get argument names
            List<String> argNames = getExpressionArguments(e);

            // Define expression arguments with user provided values
            //e.removeAllArguments();
            for (String argName : argNames) {
                try {
                    String argStr = args.getProperty(argName, null);
                    if (StringUtils.isBlank(argStr))
                        argStr = argumentDefaults.getProperty(argName, null);
                    if (StringUtils.isBlank(argStr))
                        throw new IllegalArgumentException("Missing scoring expression argument: " + argName);
                    double argValue = Double.parseDouble(argStr);
                    e.defineArgument(argName, argValue);
                } catch (Exception ex) {
                    throw ex;
                }
            }
            if (!e.checkSyntax())
                throw new IllegalArgumentException("Syntax error in expression: " + e.getErrorMessage());

            // Calculate result
            return e.calculate();
        } catch (Exception ex) {
            if (throwExceptions)
                throw ex;
            return defaultScore;
        }
    }
}
