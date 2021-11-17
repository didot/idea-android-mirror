/* The following code was generated by JFlex 1.7.0 tweaked for IntelliJ platform */

/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Defines tokens in the Logcat Filter Query Language. The language is based on the Buganizer query language specific fields can be queried
 * independently but also, a general query. For example:
 *
 *    foo bar tag: MyTag package: com.example.app
 *
 * Matches log lines that
 *
 *   TAG.contains("MyTag") && PACKAGE.contains("com.example.app") && line.contains("foo bar")
 *
 * Definitions:
 *   term: A top level entity which can either be a string value or a key-value pair
 *   key-term: A key-value term. Matches a field named by the key with the value.
 *   value-term: A top level entity representing a string. Matches the entire log line with the value.
 *
 * There are 2 types of keys. String keys can accept quoted or unquoted values while regular keys can only take an unquoted value with no
 * whitespace. String keys can also be negated and can specify a regex match:
 * String keys examples:
 *     tag: foo
 *     tag: fo\ o
 *     tag: 'foo'
 *     tag: 'fo\'o'
 *     tag: "foo"
 *     tag: "fo\"o"
 *     -tag: foo
 *     tag~: foo|bar
 *
 * Logical operations & (and), | (or) are supported as well as parenthesis.
 *
 * Implicit grouping:
 * Terms without logical operations between them are treated as an implicit AND unless they are value terms:
 *
 *   foo bar tag: MyTag -> line.contains("foo bar") && tag.contains("MyTag")
 *
 * This file is used by Grammar-Kit to generate the lexer, parser, node types and PSI classes.
 */
package com.android.tools.idea.logcat.filters.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.android.tools.idea.logcat.filters.parser.*;
import com.intellij.psi.TokenType;


/**
 * This class is a scanner generated by 
 * <a href="http://www.jflex.de/">JFlex</a> 1.7.0
 * from the specification file <tt>LogcatFilter.flex</tt>
 */
class LogcatFilterLexer implements FlexLexer {

  /** This character denotes the end of file */
  public static final int YYEOF = -1;

  /** initial size of the lookahead buffer */
  private static final int ZZ_BUFFERSIZE = 16384;

  /** lexical states */
  public static final int YYINITIAL = 0;
  public static final int STRING_VALUE = 2;
  public static final int VALUE = 4;

  /**
   * ZZ_LEXSTATE[l] is the state in the DFA for the lexical state l
   * ZZ_LEXSTATE[l+1] is the state in the DFA for the lexical state l
   *                  at the beginning of a line
   * l is of the form l = 2*k, k a non negative integer
   */
  private static final int ZZ_LEXSTATE[] = { 
     0,  0,  1,  1,  2, 2
  };

  /** 
   * Translates characters to character classes
   * Chosen bits are [7, 7, 7]
   * Total runtime size is 1928 bytes
   */
  public static int ZZ_CMAP(int ch) {
    return ZZ_CMAP_A[(ZZ_CMAP_Y[ZZ_CMAP_Z[ch>>14]|((ch>>7)&0x7f)]<<7)|(ch&0x7f)];
  }

  /* The ZZ_CMAP_Z table has 68 entries */
  static final char ZZ_CMAP_Z[] = zzUnpackCMap(
    "\1\0\103\200");

  /* The ZZ_CMAP_Y table has 256 entries */
  static final char ZZ_CMAP_Y[] = zzUnpackCMap(
    "\1\0\1\1\53\2\1\3\22\2\1\4\37\2\1\3\237\2");

  /* The ZZ_CMAP_A table has 640 entries */
  static final char ZZ_CMAP_A[] = zzUnpackCMap(
    "\11\0\5\1\22\0\1\12\1\36\1\14\3\0\1\6\1\13\1\7\1\10\3\0\1\3\14\0\1\2\21\0"+
    "\1\34\17\0\1\11\4\0\1\15\1\0\1\26\1\0\1\22\1\31\1\25\1\0\1\20\1\0\1\27\1\17"+
    "\1\23\1\21\1\33\1\16\1\0\1\32\1\24\1\30\1\0\1\35\5\0\1\5\1\0\1\4\6\0\1\1\32"+
    "\0\1\1\337\0\1\1\177\0\13\1\35\0\2\1\5\0\1\1\57\0\1\1\40\0");

  /** 
   * Translates DFA states to action switch labels.
   */
  private static final int [] ZZ_ACTION = zzUnpackAction();

  private static final String ZZ_ACTION_PACKED_0 =
    "\3\0\1\1\1\2\1\1\1\3\1\4\1\5\1\6"+
    "\11\1\1\7\1\10\4\7\4\1\1\0\1\1\1\0"+
    "\12\1\1\0\1\7\1\0\1\7\2\1\1\0\1\1"+
    "\1\0\11\1\1\0\1\7\1\0\3\1\1\11\1\1"+
    "\1\12\1\13\3\1\2\7\3\1";

  private static int [] zzUnpackAction() {
    int [] result = new int[78];
    int offset = 0;
    offset = zzUnpackAction(ZZ_ACTION_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAction(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /** 
   * Translates a state to a row index in the transition table
   */
  private static final int [] ZZ_ROWMAP = zzUnpackRowMap();

  private static final String ZZ_ROWMAP_PACKED_0 =
    "\0\0\0\37\0\76\0\135\0\174\0\233\0\135\0\135"+
    "\0\272\0\272\0\331\0\370\0\u0117\0\u0136\0\u0155\0\u0174"+
    "\0\u0193\0\u01b2\0\u01d1\0\u01f0\0\272\0\u020f\0\u022e\0\u024d"+
    "\0\u026c\0\u028b\0\u02aa\0\u02c9\0\u02e8\0\u0307\0\u0326\0\u0345"+
    "\0\u0364\0\u0383\0\u03a2\0\u03c1\0\u03e0\0\u03ff\0\u041e\0\u043d"+
    "\0\u045c\0\u047b\0\u049a\0\u04b9\0\u04d8\0\u04f7\0\u0516\0\u0535"+
    "\0\u0554\0\272\0\u0573\0\u0592\0\u05b1\0\u05d0\0\u05ef\0\u060e"+
    "\0\u062d\0\u064c\0\u066b\0\u068a\0\u06a9\0\272\0\u06c8\0\u06e7"+
    "\0\u0307\0\u0345\0\135\0\u0706\0\135\0\135\0\u0725\0\u0744"+
    "\0\u0763\0\u049a\0\u04d8\0\u0782\0\u07a1\0\u07c0";

  private static int [] zzUnpackRowMap() {
    int [] result = new int[78];
    int offset = 0;
    offset = zzUnpackRowMap(ZZ_ROWMAP_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackRowMap(String packed, int offset, int [] result) {
    int i = 0;  /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int high = packed.charAt(i++) << 16;
      result[j++] = high | packed.charAt(i++);
    }
    return j;
  }

  /** 
   * The transition table of the DFA
   */
  private static final int [] ZZ_TRANS = zzUnpackTrans();

  private static final String ZZ_TRANS_PACKED_0 =
    "\1\4\1\5\1\4\1\6\1\4\1\7\1\10\1\11"+
    "\1\12\1\13\1\5\1\14\1\15\1\16\1\17\1\20"+
    "\3\4\1\21\4\4\1\22\1\23\5\4\1\24\1\5"+
    "\5\24\2\25\1\26\1\5\1\27\1\30\22\24\1\31"+
    "\1\5\10\31\1\5\24\31\1\4\1\0\5\4\2\0"+
    "\1\13\1\0\24\4\1\0\1\5\10\0\1\5\24\0"+
    "\1\4\1\0\5\4\2\0\1\13\1\0\2\4\1\32"+
    "\1\33\1\34\3\4\1\21\4\4\1\35\6\4\37\0"+
    "\1\4\1\0\5\4\2\0\1\13\25\4\1\14\1\36"+
    "\5\14\2\36\1\37\1\36\1\4\23\14\1\15\1\40"+
    "\5\15\2\40\1\41\1\40\1\15\1\4\22\15\1\4"+
    "\1\0\5\4\2\0\1\13\1\0\3\4\1\42\6\4"+
    "\1\43\12\4\1\0\5\4\2\0\1\13\1\0\2\4"+
    "\1\44\22\4\1\0\5\4\2\0\1\13\1\0\5\4"+
    "\1\45\1\4\1\46\15\4\1\0\5\4\2\0\1\13"+
    "\1\0\7\4\1\47\1\4\1\50\13\4\1\0\5\4"+
    "\2\0\1\13\1\0\2\4\1\50\15\4\1\51\4\4"+
    "\1\0\5\4\2\0\1\13\1\0\17\4\1\52\4\4"+
    "\1\24\1\0\5\24\2\0\1\26\1\0\25\24\1\0"+
    "\5\24\2\0\1\26\25\24\1\27\1\53\5\27\2\53"+
    "\1\54\1\53\1\24\23\27\1\30\1\55\5\30\2\55"+
    "\1\56\1\55\1\30\1\24\22\30\1\31\1\0\10\31"+
    "\1\0\24\31\1\4\1\0\5\4\2\0\1\13\1\0"+
    "\3\4\1\57\21\4\1\0\5\4\2\0\1\13\1\0"+
    "\2\4\1\60\22\4\1\0\5\4\2\0\1\13\1\0"+
    "\5\4\1\45\17\4\1\0\5\4\2\0\1\13\1\0"+
    "\2\4\1\50\21\4\11\36\1\61\1\36\1\62\23\36"+
    "\1\14\1\36\5\14\2\36\1\37\25\14\11\40\1\63"+
    "\2\40\1\62\22\40\1\15\1\40\5\15\2\40\1\41"+
    "\25\15\1\4\1\0\5\4\2\0\1\13\1\0\3\4"+
    "\1\64\21\4\1\0\5\4\2\0\1\13\1\0\7\4"+
    "\1\65\15\4\1\0\5\4\2\0\1\13\1\0\13\4"+
    "\1\66\11\4\1\0\5\4\2\0\1\13\1\0\6\4"+
    "\1\67\16\4\1\0\5\4\2\0\1\13\1\0\22\4"+
    "\1\70\2\4\1\0\5\4\2\0\1\13\1\0\11\4"+
    "\1\71\13\4\1\0\5\4\2\0\1\13\1\0\12\4"+
    "\1\72\12\4\1\0\5\4\2\0\1\13\1\0\21\4"+
    "\1\73\3\4\1\0\5\4\2\0\1\13\1\0\20\4"+
    "\1\74\3\4\11\53\1\75\1\53\1\76\23\53\1\27"+
    "\1\53\5\27\2\53\1\54\25\27\11\55\1\77\2\55"+
    "\1\76\22\55\1\30\1\55\5\30\2\55\1\56\25\30"+
    "\1\4\1\0\5\4\2\0\1\13\1\0\3\4\1\72"+
    "\21\4\1\0\5\4\2\0\1\13\1\0\13\4\1\100"+
    "\10\4\11\36\1\61\1\36\1\101\23\36\11\40\1\63"+
    "\2\40\1\102\22\40\1\4\1\0\1\103\1\4\1\104"+
    "\2\4\2\0\1\13\1\0\23\4\1\105\1\4\1\0"+
    "\1\106\4\4\2\0\1\13\1\0\25\4\1\0\5\4"+
    "\2\0\1\13\1\0\14\4\1\107\10\4\1\0\5\4"+
    "\2\0\1\13\1\0\7\4\1\72\15\4\1\0\5\4"+
    "\2\0\1\13\1\0\7\4\1\110\15\4\1\0\5\4"+
    "\2\0\1\13\1\0\11\4\1\111\13\4\1\0\1\103"+
    "\1\4\1\104\2\4\2\0\1\13\1\0\25\4\1\0"+
    "\5\4\2\0\1\13\1\0\7\4\1\46\15\4\1\0"+
    "\5\4\2\0\1\13\1\0\10\4\1\51\13\4\11\53"+
    "\1\75\1\53\1\112\23\53\11\55\1\77\2\55\1\113"+
    "\22\55\1\4\1\0\5\4\2\0\1\13\1\0\14\4"+
    "\1\111\10\4\1\0\1\103\4\4\2\0\1\13\1\0"+
    "\25\4\1\0\5\4\2\0\1\13\1\0\2\4\1\114"+
    "\22\4\1\0\5\4\2\0\1\13\1\0\4\4\1\65"+
    "\20\4\1\0\5\4\2\0\1\13\1\0\2\4\1\115"+
    "\22\4\1\0\5\4\2\0\1\13\1\0\12\4\1\116"+
    "\12\4\1\0\5\4\2\0\1\13\1\0\12\4\1\67"+
    "\12\4\1\0\5\4\2\0\1\13\1\0\7\4\1\64"+
    "\14\4";

  private static int [] zzUnpackTrans() {
    int [] result = new int[2015];
    int offset = 0;
    offset = zzUnpackTrans(ZZ_TRANS_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackTrans(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      value--;
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }


  /* error codes */
  private static final int ZZ_UNKNOWN_ERROR = 0;
  private static final int ZZ_NO_MATCH = 1;
  private static final int ZZ_PUSHBACK_2BIG = 2;

  /* error messages for the codes above */
  private static final String[] ZZ_ERROR_MSG = {
    "Unknown internal scanner error",
    "Error: could not match input",
    "Error: pushback value was too large"
  };

  /**
   * ZZ_ATTRIBUTE[aState] contains the attributes of state <code>aState</code>
   */
  private static final int [] ZZ_ATTRIBUTE = zzUnpackAttribute();

  private static final String ZZ_ATTRIBUTE_PACKED_0 =
    "\3\0\5\1\2\11\12\1\1\11\10\1\1\0\1\1"+
    "\1\0\12\1\1\0\1\1\1\0\3\1\1\0\1\11"+
    "\1\0\11\1\1\0\1\11\1\0\17\1";

  private static int [] zzUnpackAttribute() {
    int [] result = new int[78];
    int offset = 0;
    offset = zzUnpackAttribute(ZZ_ATTRIBUTE_PACKED_0, offset, result);
    return result;
  }

  private static int zzUnpackAttribute(String packed, int offset, int [] result) {
    int i = 0;       /* index in packed string  */
    int j = offset;  /* index in unpacked array */
    int l = packed.length();
    while (i < l) {
      int count = packed.charAt(i++);
      int value = packed.charAt(i++);
      do result[j++] = value; while (--count > 0);
    }
    return j;
  }

  /** the input device */
  private java.io.Reader zzReader;

  /** the current state of the DFA */
  private int zzState;

  /** the current lexical state */
  private int zzLexicalState = YYINITIAL;

  /** this buffer contains the current text to be matched and is
      the source of the yytext() string */
  private CharSequence zzBuffer = "";

  /** the textposition at the last accepting state */
  private int zzMarkedPos;

  /** the current text position in the buffer */
  private int zzCurrentPos;

  /** startRead marks the beginning of the yytext() string in the buffer */
  private int zzStartRead;

  /** endRead marks the last character in the buffer, that has been read
      from input */
  private int zzEndRead;

  /**
   * zzAtBOL == true <=> the scanner is currently at the beginning of a line
   */
  private boolean zzAtBOL = true;

  /** zzAtEOF == true <=> the scanner is at the EOF */
  private boolean zzAtEOF;

  /** denotes if the user-EOF-code has already been executed */
  private boolean zzEOFDone;


  /**
   * Creates a new scanner
   *
   * @param   in  the java.io.Reader to read input from.
   */
  LogcatFilterLexer(java.io.Reader in) {
    this.zzReader = in;
  }


  /** 
   * Unpacks the compressed character translation table.
   *
   * @param packed   the packed character translation table
   * @return         the unpacked character translation table
   */
  private static char [] zzUnpackCMap(String packed) {
    int size = 0;
    for (int i = 0, length = packed.length(); i < length; i += 2) {
      size += packed.charAt(i);
    }
    char[] map = new char[size];
    int i = 0;  /* index in packed string  */
    int j = 0;  /* index in unpacked array */
    while (i < packed.length()) {
      int  count = packed.charAt(i++);
      char value = packed.charAt(i++);
      do map[j++] = value; while (--count > 0);
    }
    return map;
  }

  public final int getTokenStart() {
    return zzStartRead;
  }

  public final int getTokenEnd() {
    return getTokenStart() + yylength();
  }

  public void reset(CharSequence buffer, int start, int end, int initialState) {
    zzBuffer = buffer;
    zzCurrentPos = zzMarkedPos = zzStartRead = start;
    zzAtEOF  = false;
    zzAtBOL = true;
    zzEndRead = end;
    yybegin(initialState);
  }

  /**
   * Refills the input buffer.
   *
   * @return      {@code false}, iff there was new input.
   *
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  private boolean zzRefill() throws java.io.IOException {
    return true;
  }


  /**
   * Returns the current lexical state.
   */
  public final int yystate() {
    return zzLexicalState;
  }


  /**
   * Enters a new lexical state
   *
   * @param newState the new lexical state
   */
  public final void yybegin(int newState) {
    zzLexicalState = newState;
  }


  /**
   * Returns the text matched by the current regular expression.
   */
  public final CharSequence yytext() {
    return zzBuffer.subSequence(zzStartRead, zzMarkedPos);
  }


  /**
   * Returns the character at position {@code pos} from the
   * matched text.
   *
   * It is equivalent to yytext().charAt(pos), but faster
   *
   * @param pos the position of the character to fetch.
   *            A value from 0 to yylength()-1.
   *
   * @return the character at position pos
   */
  public final char yycharat(int pos) {
    return zzBuffer.charAt(zzStartRead+pos);
  }


  /**
   * Returns the length of the matched text region.
   */
  public final int yylength() {
    return zzMarkedPos-zzStartRead;
  }


  /**
   * Reports an error that occurred while scanning.
   *
   * In a wellformed scanner (no or only correct usage of
   * yypushback(int) and a match-all fallback rule) this method
   * will only be called with things that "Can't Possibly Happen".
   * If this method is called, something is seriously wrong
   * (e.g. a JFlex bug producing a faulty scanner etc.).
   *
   * Usual syntax/scanner level error handling should be done
   * in error fallback rules.
   *
   * @param   errorCode  the code of the errormessage to display
   */
  private void zzScanError(int errorCode) {
    String message;
    try {
      message = ZZ_ERROR_MSG[errorCode];
    }
    catch (ArrayIndexOutOfBoundsException e) {
      message = ZZ_ERROR_MSG[ZZ_UNKNOWN_ERROR];
    }

    throw new Error(message);
  }


  /**
   * Pushes the specified amount of characters back into the input stream.
   *
   * They will be read again by then next call of the scanning method
   *
   * @param number  the number of characters to be read again.
   *                This number must not be greater than yylength()!
   */
  public void yypushback(int number)  {
    if ( number > yylength() )
      zzScanError(ZZ_PUSHBACK_2BIG);

    zzMarkedPos -= number;
  }


  /**
   * Resumes scanning until the next regular expression is matched,
   * the end of input is encountered or an I/O-Error occurs.
   *
   * @return      the next token
   * @exception   java.io.IOException  if any I/O-Error occurs
   */
  public IElementType advance() throws java.io.IOException {
    int zzInput;
    int zzAction;

    // cached fields:
    int zzCurrentPosL;
    int zzMarkedPosL;
    int zzEndReadL = zzEndRead;
    CharSequence zzBufferL = zzBuffer;

    int [] zzTransL = ZZ_TRANS;
    int [] zzRowMapL = ZZ_ROWMAP;
    int [] zzAttrL = ZZ_ATTRIBUTE;

    while (true) {
      zzMarkedPosL = zzMarkedPos;

      zzAction = -1;

      zzCurrentPosL = zzCurrentPos = zzStartRead = zzMarkedPosL;

      zzState = ZZ_LEXSTATE[zzLexicalState];

      // set up zzAction for empty match case:
      int zzAttributes = zzAttrL[zzState];
      if ( (zzAttributes & 1) == 1 ) {
        zzAction = zzState;
      }


      zzForAction: {
        while (true) {

          if (zzCurrentPosL < zzEndReadL) {
            zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL/*, zzEndReadL*/);
            zzCurrentPosL += Character.charCount(zzInput);
          }
          else if (zzAtEOF) {
            zzInput = YYEOF;
            break zzForAction;
          }
          else {
            // store back cached positions
            zzCurrentPos  = zzCurrentPosL;
            zzMarkedPos   = zzMarkedPosL;
            boolean eof = zzRefill();
            // get translated positions and possibly new buffer
            zzCurrentPosL  = zzCurrentPos;
            zzMarkedPosL   = zzMarkedPos;
            zzBufferL      = zzBuffer;
            zzEndReadL     = zzEndRead;
            if (eof) {
              zzInput = YYEOF;
              break zzForAction;
            }
            else {
              zzInput = Character.codePointAt(zzBufferL, zzCurrentPosL/*, zzEndReadL*/);
              zzCurrentPosL += Character.charCount(zzInput);
            }
          }
          int zzNext = zzTransL[ zzRowMapL[zzState] + ZZ_CMAP(zzInput) ];
          if (zzNext == -1) break zzForAction;
          zzState = zzNext;

          zzAttributes = zzAttrL[zzState];
          if ( (zzAttributes & 1) == 1 ) {
            zzAction = zzState;
            zzMarkedPosL = zzCurrentPosL;
            if ( (zzAttributes & 8) == 8 ) break zzForAction;
          }

        }
      }

      // store back cached position
      zzMarkedPos = zzMarkedPosL;

      if (zzInput == YYEOF && zzStartRead == zzCurrentPos) {
        zzAtEOF = true;
        return null;
      }
      else {
        switch (zzAction < 0 ? zzAction : ZZ_ACTION[zzAction]) {
          case 1: 
            { return LogcatFilterTypes.VALUE;
            } 
            // fall through
          case 12: break;
          case 2: 
            { return TokenType.WHITE_SPACE;
            } 
            // fall through
          case 13: break;
          case 3: 
            { return LogcatFilterTypes.OR;
            } 
            // fall through
          case 14: break;
          case 4: 
            { return LogcatFilterTypes.AND;
            } 
            // fall through
          case 15: break;
          case 5: 
            { return LogcatFilterTypes.LPAREN;
            } 
            // fall through
          case 16: break;
          case 6: 
            { return LogcatFilterTypes.RPAREN;
            } 
            // fall through
          case 17: break;
          case 7: 
            { yybegin(YYINITIAL); return LogcatFilterTypes.KVALUE;
            } 
            // fall through
          case 18: break;
          case 8: 
            { return TokenType.BAD_CHARACTER;
            } 
            // fall through
          case 19: break;
          case 9: 
            { yybegin(STRING_VALUE); return LogcatFilterTypes.KEY;
            } 
            // fall through
          case 20: break;
          case 10: 
            { return LogcatFilterTypes.PROJECT_APP;
            } 
            // fall through
          case 21: break;
          case 11: 
            { yybegin(VALUE); return LogcatFilterTypes.KEY;
            } 
            // fall through
          case 22: break;
          default:
            zzScanError(ZZ_NO_MATCH);
          }
      }
    }
  }


}
