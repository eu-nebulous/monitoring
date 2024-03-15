/*
 * Copyright (C) 2023-2025 Institute of Communication and Computer Systems (imu.iccs.gr)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v2.0.
 * If a copy of the MPL was not distributed with this file, you can obtain one at
 * https://www.mozilla.org/en-US/MPL/2.0/
 */

grammar Constraints;

constraintExpression
    : orConstraint EOF ;

orConstraint
    : andConstraint ( OR andConstraint )*
    ;

andConstraint
    : constraint ( AND constraint )*
    ;

constraint
    : PARENTHESES_OPEN orConstraint PARENTHESES_CLOSE
    | metricConstraint
    | notConstraint
    | conditionalConstraint
    ;

metricConstraint
    : ID comparisonOperator NUM
    | NUM comparisonOperator ID
    ;

comparisonOperator
    : '=' | '==' | '<>'
    | '<' | '<=' | '=<'
    | '>' | '>=' | '=>'
    ;

notConstraint
    : NOT constraint
    ;

/*logicalOperator
    : AND | OR
    ;*/

conditionalConstraint
    : IF orConstraint THEN orConstraint ( ELSE orConstraint )?
    ;

PARENTHESES_OPEN:  '(' ;
PARENTHESES_CLOSE: ')' ;

NOT: N O T ;
AND: A N D ;
OR:  O R ;

IF: I F ;
THEN: T H E N ;
ELSE: E L S E ;

fragment A: 'a' | 'A' ;
fragment D: 'd' | 'D' ;
fragment E: 'e' | 'E' ;
fragment F: 'f' | 'F' ;
fragment H: 'h' | 'H' ;
fragment I: 'i' | 'I' ;
fragment L: 'l' | 'L' ;
fragment N: 'n' | 'N' ;
fragment O: 'o' | 'O' ;
fragment R: 'r' | 'R' ;
fragment S: 's' | 'S' ;
fragment T: 't' | 'T' ;

ID: [a-zA-Z] [a-zA-Z0-9_-]* ;

NUM:  ('-' | '+')? [0-9]+ ('.' [0-9]+)?
    | ('-' | '+')? '.' [0-9]+
    ;

WS: [ \r\n\t]+ -> skip ;
