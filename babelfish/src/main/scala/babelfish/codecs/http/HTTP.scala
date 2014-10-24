/*
 * Copyright (c) 2014 Landon Fuller <landon@landonf.org>
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package babelfish.codecs.http

import java.nio.charset.StandardCharsets
import scodec.Codec
import scodec.codecs._

/**
 * An HTTP transaction logged by SSLsplit.
 *
 * @param request HTTP request.
 * @param response HTTP response.
 */
case class HTTP (request: Request, response: Response)

/**
 * Parser for HTTP log files generated by Net-Monitor/SSLsplit.
 */
object HTTP {
  /** We use ASCII for HTTP header values */
  implicit private val charset = StandardCharsets.US_ASCII
  import babelfish.codecs._
  import RegexCodec.syntax._

  /** Horizontal whitespace */
  private val ws = """^[^\S\r\n]+""".r.unit(" ")

  /** Vertical whitespace */
  private val vs = """(\r\n|\r|\n)+""".r.unit("\r\n")

  /** HTTP header */
  private val header = """[^:\r\n]+""".r :: """:\s*""".r.unit(": ") ~> """[^\r\n]*""".r

  /** HTTP request/response body */
  private def body (headers: Headers) = vs ~> (headers.contentLength match {
    case Left(msg) =>
      /* Try parsing the HTTP message as if the body is zero-length */
      bytes(0)
    case Right(length) => bytes(length)
  })

  /** Request */
  private val request = {
    """^(POST|PUT|GET|HEAD|DELETE|OPTIONS|TRACE|CONNECT)""".r ::
      ws ~> """\S+""".r ::
      ws ~> """HTTP\/[0-9.]+""".r ::
      vs ~> """Host:\s+""".r.unit("Host: ") ~> """\S+""".r ::
      zeroOrMore ({
        vs ~> header
      }.as[Header]).as[Headers].flatZipHList(body)
  }.as[Request]

  /** Response */
  private val response = {
    """HTTP\/[0-9.]+""".r ::
      ws ~> """[0-9]+""".r ::
      ws ~> """[^\r\n]+""".r ::
      zeroOrMore ({
        vs ~> header
      }.as[Header]).as[Headers].flatZipHList(body)
  }.as[Response]

  /** Full HTTP log file */
  private[codecs] def http: Codec[HTTP] = (
    request :: response
  ).as[HTTP]
}
