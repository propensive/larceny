/*
    Larceny, version 1.0.0. Copyright 2023-23 Jon Pretty, Propensive OÜ.

    The primary distribution site is: https://propensive.com/

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
    either express or implied. See the License for the specific language governing permissions
    and limitations under the License.
*/

package larceny

@main
def run(): Unit =
  val errors = captureCompileErrors:
    val x = 10
    "foo".substring("1")
  
  errors.foreach(println(_))
  
  val errors2 = captureCompileErrors:
    Macros.hello()

  errors2.foreach(println(_))

  val errors3 = captureCompileErrors:
    class Baz() extends Incomplete

  errors3.foreach(println(_))

trait Incomplete:
  def foo: Int