// Copyright (c) 2011, the Dart project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
// Generated by scripts/tokenizer_gen.py.


interface TokenSource {
  Token next();
}

class InterpStack {
  InterpStack next, previous;
  final int quote;
  final bool isMultiline;
  int depth;

  InterpStack(this.previous, this.quote, this.isMultiline): depth = -1;

  InterpStack pop() {
    return this.previous;
  }

  static InterpStack push(InterpStack stack, int quote, bool isMultiline) {
    var newStack = new InterpStack(stack, quote, isMultiline);
    if (stack != null) newStack.previous = stack;
    return newStack;
  }
}

/**
 * The base class for our tokenizer. The hand coded parts are in this file, with
 * the generated parts in the subclass Tokenizer.
 */
class TokenizerBase extends TokenizerHelpers implements TokenSource {
  final SourceFile _source;
  final bool _skipWhitespace;
  String _text;

  int _index;
  int _startIndex;

  /** Keeps track of string interpolation state. */
  InterpStack _interpStack;

  TokenizerBase(this._source, this._skipWhitespace, [index = 0])
      : this._index = index {
    _text = _source.text;
  }

  abstract Token next();
  abstract int getIdentifierKind();

  int _nextChar() {
    if (_index < _text.length) {
      return _text.charCodeAt(_index++);
    } else {
      return 0;
    }
  }

  int _peekChar() {
    if (_index < _text.length) {
      return _text.charCodeAt(_index);
    } else {
      return 0;
    }
  }

  bool _maybeEatChar(int ch) {
    if (_index < _text.length) {
      if (_text.charCodeAt(_index) == ch) {
        _index++;
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  String _tokenText() {
    if (_index < _text.length) {
      return _text.substring(_startIndex, _index);
    } else {
      return _text.substring(_startIndex, _text.length);
    }
  }

  Token _finishToken(int kind) {
    return new Token(kind, _source, _startIndex, _index);
  }

  Token _errorToken([String message = null]) {
    return new ErrorToken(
        TokenKind.ERROR, _source, _startIndex, _index, message);
  }

  Token finishWhitespace() {
    _index--;
    while (_index < _text.length) {
      final ch = _text.charCodeAt(_index++);
      if (ch == 32/*' '*/ || ch == 9/*'\t'*/ || ch == 13/*'\r'*/) {
        // do nothing
      } else if (ch == 10/*'\n'*/) {
        if (!_skipWhitespace) {
          return _finishToken(TokenKind.WHITESPACE); // note the newline?
        }
      } else {
        _index--;
        if (_skipWhitespace) {
          return next();
        } else {
          return _finishToken(TokenKind.WHITESPACE);
        }
      }

    }
    return _finishToken(TokenKind.END_OF_FILE);
  }

  Token finishHashBang() {
    while (true) {
      int ch = _nextChar();
      if (ch == 0 || ch == 10/*'\n'*/ || ch == 13/*'\r'*/) {
        return _finishToken(TokenKind.HASHBANG);
      }
    }
  }

  Token finishSingleLineComment() {
    while (true) {
      int ch = _nextChar();
      if (ch == 0 || ch == 10/*'\n'*/ || ch == 13/*'\r'*/) {
        if (_skipWhitespace) {
          return next();
        } else {
          return _finishToken(TokenKind.COMMENT);
        }
      }
    }
  }

  Token finishMultiLineComment() {
    int nesting = 1;
    do {
      int ch = _nextChar();
      if (ch == 0) {
        return _errorToken();
      } else if (ch == 42/*'*'*/) {
        if (_maybeEatChar(47/*'/'*/)) {
          nesting--;
        }
      } else if (ch == 47/*'/'*/) {
        if (_maybeEatChar(42/*'*'*/)) {
          nesting++;
        }
      }
    } while (nesting > 0);

    if (_skipWhitespace) {
      return next();
    } else {
      return _finishToken(TokenKind.COMMENT);
    }
  }

  void eatDigits() {
    while (_index < _text.length) {
      if (isDigit(_text.charCodeAt(_index))) {
        _index++;
      } else {
        return;
      }
    }
  }

  static int _hexDigit(int c) {
    if(c >= 48/*0*/ && c <= 57/*9*/) {
      return c - 48;
    } else if (c >= 97/*a*/ && c <= 102/*f*/) {
      return c - 87;
    } else if (c >= 65/*A*/ && c <= 70/*F*/) {
      return c - 55;
    } else {
      return -1;
    }
  }

  int readHex([int hexLength]) {
    int maxIndex;
    if (hexLength === null) {
      maxIndex = _text.length - 1;
    } else {
      // TODO(jimhug): What if this is too long?
      maxIndex = _index + hexLength;
      if (maxIndex >= _text.length) return -1;
    }
    var result = 0;
    while (_index < maxIndex) {
      final digit = _hexDigit(_text.charCodeAt(_index));
      if (digit == -1) {
        if (hexLength === null) {
          return result;
        } else {
          return -1;
        }
      }
      _hexDigit(_text.charCodeAt(_index));
      // Multiply by 16 rather than shift by 4 since that will result in a
      // correct value for numbers that exceed the 32 bit precision of JS
      // 'integers'.
      // TODO: Figure out a better solution to integer truncation. Issue 638.
      result = (result * 16) + digit;
      _index++;
    }

    return result;
  }

  Token finishHex() {
    final value = readHex();
    return new LiteralToken(TokenKind.HEX_INTEGER, _source, _startIndex,
        _index, value);
  }

  Token finishNumber() {
    eatDigits();

    if (_peekChar() == 46/*.*/) {
      // Handle the case of 1.toString().
      _nextChar();
      if (isDigit(_peekChar())) {
        eatDigits();
        return finishNumberExtra(TokenKind.DOUBLE);
      } else {
        _index--;
      }
    }

    return finishNumberExtra(TokenKind.INTEGER);
  }

  Token finishNumberExtra(int kind) {
    if (_maybeEatChar(101/*e*/) || _maybeEatChar(69/*E*/)) {
      kind = TokenKind.DOUBLE;
      _maybeEatChar(45/*-*/);
      _maybeEatChar(43/*+*/);
      eatDigits();
    }
    if (_peekChar() != 0 && isIdentifierStart(_peekChar())) {
      _nextChar();
      return _errorToken("illegal character in number");
    }

    return _finishToken(kind);
  }

  Token _makeStringToken(List<int> buf, bool isPart) {
    final s = new String.fromCharCodes(buf);
    final kind = isPart ? TokenKind.STRING_PART : TokenKind.STRING;
    return new LiteralToken(kind, _source, _startIndex, _index, s);
  }

  Token _makeRawStringToken(bool isMultiline) {
    String s;
    if (isMultiline) {
      // Skip initial newline in multiline strings
      if (_source.text[_startIndex + 4] == '\n') {
        s = _source.text.substring(_startIndex + 5, _index - 3);
      } else {
        s = _source.text.substring(_startIndex + 4, _index - 3);
      }
    } else {
      s = _source.text.substring(_startIndex + 2, _index - 1);
    }
    return new LiteralToken(TokenKind.STRING, _source, _startIndex, _index, s);
  }

  Token finishMultilineString(int quote) {
    var buf = new List<int>();
    while (true) {
      int ch = _nextChar();
      if (ch == 0) {
        return _errorToken();
      } else if (ch == quote) {
        if (_maybeEatChar(quote)) {
          if (_maybeEatChar(quote)) {
            return _makeStringToken(buf, false);
          }
          buf.add(quote);
        }
        buf.add(quote);
      } else if (ch == 36/*$*/) {
        // start of string interp
        _interpStack = InterpStack.push(_interpStack, quote, true);
        return _makeStringToken(buf, true);
      } else if (ch == 92/*\*/) {
        var escapeVal = readEscapeSequence();
        if (escapeVal == -1) {
          return _errorToken("invalid hex escape sequence");
        } else {
          buf.add(escapeVal);
        }
      } else {
        buf.add(ch);
      }
    }
  }

  Token _finishOpenBrace() {
    if (_interpStack != null) {
      if (_interpStack.depth == -1) {
        _interpStack.depth = 1;
      } else {
        assert(_interpStack.depth >= 0);
        _interpStack.depth += 1;
      }
    }
    return _finishToken(TokenKind.LBRACE);
  }

  Token _finishCloseBrace() {
    if (_interpStack != null) {
      _interpStack.depth -= 1;
      assert(_interpStack.depth >= 0);
    }
    return _finishToken(TokenKind.RBRACE);
  }

  Token finishString(int quote) {
    if (_maybeEatChar(quote)) {
      if (_maybeEatChar(quote)) {
        // skip an initial newline
        _maybeEatChar(10/*'\n'*/);
        return finishMultilineString(quote);
      } else {
        return _makeStringToken(new List<int>(), false);
      }
    }
    return finishStringBody(quote);
  }

  Token finishRawString(int quote) {
    if (_maybeEatChar(quote)) {
      if (_maybeEatChar(quote)) {
        return finishMultilineRawString(quote);
      } else {
        return _makeStringToken(new List<int>(), false);
      }
    }
    while (true) {
      int ch = _nextChar();
      if (ch == quote) {
        return _makeRawStringToken(false);
      } else if (ch == 0) {
        return _errorToken();
      }
    }
  }

  Token finishMultilineRawString(int quote) {
    while (true) {
      int ch = _nextChar();
      if (ch == 0) {
        return _errorToken();
      } else if (ch == quote && _maybeEatChar(quote) && _maybeEatChar(quote)) {
        return _makeRawStringToken(true);
      }
    }
  }

  Token finishStringBody(int quote) {
    var buf = new List<int>();
    while (true) {
      int ch = _nextChar();
      if (ch == quote) {
        return _makeStringToken(buf, false);
      } else if (ch == 36/*$*/) {
        // start of string interp
        _interpStack = InterpStack.push(_interpStack, quote, false);
        return _makeStringToken(buf, true);
      } else if (ch == 0) {
        return _errorToken();
      } else if (ch == 92/*\*/) {
        var escapeVal = readEscapeSequence();
        if (escapeVal == -1) {
          return _errorToken("invalid hex escape sequence");
        } else {
          buf.add(escapeVal);
        }
      } else {
        buf.add(ch);
      }
    }
  }

  int readEscapeSequence() {
    final ch = _nextChar();
    int hexValue;
    switch (ch) {
      case 110/*n*/:
        return 0x0a/*'\n'*/;
      case 114/*r*/:
        return 0x0d/*'\r'*/;
      case 102/*f*/:
        return 0x0c/*'\f'*/;
      case 98/*b*/:
        return 0x08/*'\b'*/;
      case 116/*t*/:
        return 0x09/*'\t'*/;
      case 118/*v*/:
        return 0x0b/*'\v'*/;
      case 120/*x*/:
        hexValue = readHex(2);
        break;
      case 117/*u*/:
        if (_maybeEatChar(123/*{*/)) {
          hexValue = readHex();
          if (!_maybeEatChar(125/*}*/)) {
            return -1;
          } else {
            break;
          }
        } else {
          hexValue = readHex(4);
          break;
        }
      default: return ch;
    }

    if (hexValue == -1) return -1;

    // According to the Unicode standard the high and low surrogate halves
    // used by UTF-16 (U+D800 through U+DFFF) and values above U+10FFFF
    // are not legal Unicode values.
    if (hexValue < 0xD800 || hexValue > 0xDFFF && hexValue <= 0xFFFF) {
      return hexValue;
    } else if (hexValue <= 0x10FFFF){
      world.fatal('unicode values greater than 2 bytes not implemented yet');
      return -1;
    } else {
      return -1;
    }
  }

  Token finishDot() {
    if (isDigit(_peekChar())) {
      eatDigits();
      return finishNumberExtra(TokenKind.DOUBLE);
    } else {
      return _finishToken(TokenKind.DOT);
    }
  }

  Token finishIdentifier(int ch) {
    if (_interpStack != null && _interpStack.depth == -1) {
      _interpStack.depth = 0;
      if (ch == 36/*$*/) {
        return _errorToken(
            @"illegal character after $ in string interpolation");
      }
      while (_index < _text.length) {
        if (!isInterpIdentifierPart(_text.charCodeAt(_index++))) {
          _index--;
          break;
        }
      }
    } else {
      while (_index < _text.length) {
        if (!isIdentifierPart(_text.charCodeAt(_index++))) {
          _index--;
          break;
        }
      }
    }
    int kind = getIdentifierKind();
    if (kind == TokenKind.IDENTIFIER) {
      return _finishToken(TokenKind.IDENTIFIER);
    } else {
      return _finishToken(kind);
    }
  }
}
