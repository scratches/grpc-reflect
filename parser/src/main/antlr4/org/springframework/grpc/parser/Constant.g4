grammar Constant;

// lexical

constant
    : fullIdent
    | (MINUS | PLUS)? intLit
    | ( MINUS | PLUS)? floatLit
    | strLit
    | boolLit
    | blockLit
    ;

blockLit
    : LC (ident COLON constant)* RC
    ;

// Lexical elements

ident
    : IDENTIFIER
    ;

fullIdent
    : ident (DOT ident)*
    ;

intLit
    : INT_LIT
    ;

strLit
    : STR_LIT*
    ;

boolLit
    : BOOL_LIT
    ;

floatLit
    : FLOAT_LIT
    ;

// symbols

DOT
    : '.'
    ;

COLON
    : ':'
    ;

LC
    : '{'
    ;

RC
    : '}'
    ;

PLUS
    : '+'
    ;

MINUS
    : '-'
    ;


BOOL_LIT
    : 'true'
    | 'false'
    ;

FLOAT_LIT
    : (DECIMALS DOT DECIMALS? EXPONENT? | DECIMALS EXPONENT | DOT DECIMALS EXPONENT?)
    | 'inf'
    | 'nan'
    ;

fragment EXPONENT
    : ('e' | 'E') (PLUS | MINUS)? DECIMALS
    ;

fragment DECIMALS
    : DECIMAL_DIGIT+
    ;

INT_LIT
    : DECIMAL_LIT
    | OCTAL_LIT
    | HEX_LIT
    ;

fragment DECIMAL_LIT
    : [1-9] DECIMAL_DIGIT*
    ;

fragment OCTAL_LIT
    : '0' OCTAL_DIGIT*
    ;

fragment HEX_LIT
    : '0' ('x' | 'X') HEX_DIGIT+
    ;

STR_LIT
    : ('\'' ( CHAR_VALUE)*? '\'')
    | ( '"' ( CHAR_VALUE)*? '"')
    ;

fragment CHAR_VALUE
    : HEX_ESCAPE
    | OCT_ESCAPE
    | CHAR_ESCAPE
    | ~[\u0000\n\\]
    ;


fragment HEX_ESCAPE
    : '\\' ('x' | 'X') HEX_DIGIT HEX_DIGIT
    ;

fragment OCT_ESCAPE
    : '\\' OCTAL_DIGIT OCTAL_DIGIT OCTAL_DIGIT
    ;

fragment CHAR_ESCAPE
    : '\\' ('a' | 'b' | 'f' | 'n' | 'r' | 't' | 'v' | '\\' | '\'' | '"')
    ;

IDENTIFIER
    : LETTER (LETTER | DECIMAL_DIGIT)*
    ;

fragment LETTER
    : [A-Za-z_]
    ;

fragment DECIMAL_DIGIT
    : [0-9]
    ;

fragment OCTAL_DIGIT
    : [0-7]
    ;

fragment HEX_DIGIT
    : [0-9A-Fa-f]
    ;

// comments
WS
    : [ \t\r\n\u000C]+ -> skip
    ;
