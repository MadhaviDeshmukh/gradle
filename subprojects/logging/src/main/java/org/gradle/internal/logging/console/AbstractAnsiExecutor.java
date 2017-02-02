/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.fusesource.jansi.Ansi;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.logging.text.Style;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.io.IOException;

public abstract class AbstractAnsiExecutor implements AnsiExecutor {
    private final Appendable target;
    private final ColorMap colorMap;
    private final boolean forceAnsi;
    private final Cursor writeCursor = new Cursor();

    public AbstractAnsiExecutor(Appendable target, ColorMap colorMap, boolean forceAnsi) {
        this.target = target;
        this.colorMap = colorMap;
        this.forceAnsi = forceAnsi;
    }

    @Override
    public void writeAt(Cursor writePos, Action<? super AnsiContext> action) {
        Ansi ansi = create();
        positionCursorAt(writePos, ansi);
        action.execute(new AnsiContextImpl(ansi, colorMap, writePos));
        write(ansi);
    }

    @Override
    public void positionCursorAt(Cursor position) {
        Ansi ansi = create();
        positionCursorAt(position, ansi);
        write(ansi);
    }

    private void charactersWritten(Cursor cursor, int count) {
        writeCursor.col += count;
        cursor.copyFrom(writeCursor);
    }

    private void newLineWritten(Cursor cursor) {
        writeCursor.col = 0;

        // On any line except the bottom most one, a new line simply move the cursor to the next row.
        // Note: the next row has a lower index.
        if (writeCursor.row > 0) {
            writeCursor.row--;
        } else {
            writeCursor.row = 0;

            doNewLineAdjustment();
        }
        cursor.copyFrom(writeCursor);
    }

    protected abstract void doNewLineAdjustment();

    private void positionCursorAt(Cursor position, Ansi ansi) {
        if (writeCursor.row == position.row) {
            if (writeCursor.col == position.col) {
                return;
            }
            if (writeCursor.col < position.col) {
                ansi.cursorRight(position.col - writeCursor.col);
            } else {
                ansi.cursorLeft(writeCursor.col - position.col);
            }
        } else {
            if (writeCursor.col > 0) {
                ansi.cursorLeft(writeCursor.col);
            }
            if (writeCursor.row < position.row) {
                ansi.cursorUp(position.row - writeCursor.row);
            } else {
                ansi.cursorDown(writeCursor.row - position.row);
            }
            if (position.col > 0) {
                ansi.cursorRight(position.col);
            }
        }
        writeCursor.copyFrom(position);
    }

    private Ansi create() {
        if (forceAnsi) {
            return new Ansi();
        } else {
            return Ansi.ansi();
        }
    }

    private void write(Ansi ansi) {
        try {
            target.append(ansi.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private class AnsiContextImpl implements AnsiContext {
        private final Ansi delegate;
        private final ColorMap colorMap;
        private final Cursor writePos;

        AnsiContextImpl(Ansi delegate, ColorMap colorMap, Cursor writePos) {
            this.delegate = delegate;
            this.colorMap = colorMap;
            this.writePos = writePos;
        }

        @Override
        public AnsiContext withColor(ColorMap.Color color, Action<? super AnsiContext> action) {
            color.on(delegate);
            action.execute(this);
            color.off(delegate);
            return this;
        }

        @Override
        public AnsiContext withStyle(Style style, Action<? super AnsiContext> action) {
            return withColor(colorMap.getColourFor(style), action);
        }

        @Override
        public AnsiContext withStyle(StyledTextOutput.Style style, Action<? super AnsiContext> action) {
            return withColor(colorMap.getColourFor(style), action);
        }

        @Override
        public AnsiContext a(CharSequence value) {
            delegate.a(value);
            charactersWritten(writePos, value.length());
            return this;
        }

        @Override
        public AnsiContext newline() {
            delegate.newline();
            newLineWritten(writePos);
            return this;
        }

        @Override
        public AnsiContext eraseForward() {
            delegate.eraseLine(Ansi.Erase.FORWARD);
            return this;
        }
    }
}