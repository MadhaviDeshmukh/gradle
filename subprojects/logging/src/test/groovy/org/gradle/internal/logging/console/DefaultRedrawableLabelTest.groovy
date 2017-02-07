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

package org.gradle.internal.logging.console

import org.fusesource.jansi.Ansi
import org.gradle.internal.logging.text.Span
import org.gradle.internal.logging.text.Style
import spock.lang.Specification
import spock.lang.Unroll

class DefaultRedrawableLabelTest extends Specification{
    def ansi = Mock(Ansi)
    def factory = new AnsiFactory() {
        Ansi create() {
            return ansi
        }
    }
    def writeCursor = Cursor.at(42, 0);
    def target = Stub(Appendable)
    def colorMap = new DefaultColorMap()
    def listener = Mock(DefaultAnsiExecutor.NewLineListener)
    def ansiExecutor = new DefaultAnsiExecutor(target, colorMap, factory, writeCursor, listener);
    def label = new DefaultRedrawableLabel(ansiExecutor, Cursor.from(writeCursor))


    def "setting plain text to the label will only write the text to ansi"() {
        given:
        label.text = "text"

        when:
        label.redraw()

        then:
        1 * ansi.a("text")
        0 * ansi._
    }

    def "setting styled text (emphasis only) to the label will change the style and write the text to ansi"() {
        given:
        label.text = new Span(Style.of(Style.Emphasis.BOLD), "text")

        when:
        label.redraw()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.a('text')
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD_OFF)
        0 * ansi._
    }

    def "setting styled text (color only) to the label will change the style and write the text to ansi"() {
        given:
        label.text = new Span(Style.of(Style.Color.GREEN), "text")

        when:
        label.redraw()

        then:
        1 * ansi.fg(Ansi.Color.GREEN)
        1 * ansi.a('text')
        1 * ansi.fg(Ansi.Color.DEFAULT)
        0 * ansi._
    }

    def "setting styled text (emphasis and color) to the label will change the style and write the text to ansi"() {
        given:
        label.text = new Span(Style.of(Style.Emphasis.BOLD, Style.Color.GREEN), "text")

        when:
        label.redraw()

        then:
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD)
        1 * ansi.fg(Ansi.Color.GREEN)
        1 * ansi.a('text')
        1 * ansi.fg(Ansi.Color.DEFAULT)
        1 * ansi.a(Ansi.Attribute.INTENSITY_BOLD_OFF)
        0 * ansi._
    }

    def "write the text once to ansi when redrawing multiple time without changing the label text"() {
        given:
        label.text = "text"

        when:
        label.redraw()
        label.redraw()

        then:
        1 * ansi.a('text')
        0 * ansi._
    }

    @Unroll
    def "scrolling the label down will decrement the write position rows by #rows"() {
        given:
        int previousWriteRow = label.writePosition.row

        when:
        label.scrollDownBy(rows)

        then:
        label.writePosition.row == previousWriteRow - rows

        where:
        rows << [0, 2, 5, 9, 21]
    }

    @Unroll
    def "scrolling the label by the same number of rows in both direction between redraw won't rewrite the text to ansi"() {
        given:
        label.text = "text"

        when:
        label.redraw()
        label.scrollDownBy(rows)
        label.scrollUpBy(rows)
        label.redraw()

        then:
        1 * ansi.a('text')
        0 * ansi._

        where:
        rows << [0, 2, 5, 9, 21]
    }

    def "changing the label text between redraw will rewrite the text to ansi"() {
        given:
        label.text = "text"

        when:
        label.redraw()
        label.text = "new text"
        label.redraw()

        then:
        1 * ansi.a("text")
        1 * ansi.cursorLeft(4)
        1 * ansi.a("new text")
        0 * ansi._
    }

    def "changing the label text to a smaller string between redraw will erase the characters moving forward"() {
        given:
        label.text = "long text"

        when:
        label.redraw()
        label.text = "text"
        label.redraw()

        then:
        1 * ansi.a("long text")
        1 * ansi.cursorLeft(9)
        1 * ansi.a("text")
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        0 * ansi._
    }

    @Unroll
    def "scrolling the label by non-zero number of rows between redraw will rewrite the text to ansi to the new location"() {
        given:
        label.text = "text"

        when:
        label.redraw()
        label.scrollBy(rows)
        label.redraw()

        then:
        2 * ansi.a('text')
        1 * ansi.cursorLeft(4)
        if (rows < 0) {
            1 * ansi.cursorUp(Math.abs(rows))
        } else {
            1 * ansi.cursorDown(rows)
        }
        1 * ansi.eraseLine(Ansi.Erase.FORWARD)
        0 * ansi._

        where:
        rows << [-9, -5, -1, 1, 4, 9]
    }

    def "scrolling the label by zero row between redraw won't rewrite the text to ansi"() {
        given:
        label.text = "text"

        when:
        label.redraw()
        label.scrollBy(0)
        label.redraw()

        then:
        1 * ansi.a('text')
        0 * ansi._
    }

    def "newLineAdjustment between redraw won't rewrite the text to ansi"() {
        given:
        label.text = "text"

        when:
        label.redraw()
        label.newLineAdjustment()
        label.redraw()

        then:
        1 * ansi.a('text')
        0 * ansi._
    }

    def "clears the line on second redraw when visible is set to false after initial redraw"() {
        given:
        label.text = "text"

        when:
        label.redraw()
        label.setVisible(false)
        label.redraw()

        then:
        1 * ansi.a('text')
        1 * ansi.eraseLine(Ansi.Erase.ALL);
        0 * ansi._
    }

    def "won't redraw when label is out of console bound"() {
        given:
        def label = new DefaultRedrawableLabel(ansiExecutor, Cursor.at(-2, 0))
        label.text = "text"

        when:
        label.redraw()

        then:
        0 * ansi._
    }

    def "writes nothing when visibility is set to false before first redraw"() {
        given:
        label.text = "text"

        when:
        label.setVisible(false)
        label.redraw()

        then:
        0 * ansi._
    }
}
