#
# Copyright 2011-2012 Asakusa Framework Team.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ "$WG_CLASSPATH_DELIMITER" = "" ]
then
    _WG_CLASSPATH_DELIMITER=':'
else 
    _WG_CLASSPATH_DELIMITER=$WG_CLASSPATH_DELIMITER
fi

if [ "$_WG_CLASSPATH" = "" ]
then
    _WG_CLASSPATH="$_WG_ROOT/conf"
else
    _WG_CLASSPATH="${_WG_CLASSPATH}${_WG_CLASSPATH_DELIMITER}$_WG_ROOT/conf"
fi

if [ -d "$_WG_ROOT/lib" ]
then
    for f in $(ls "$_WG_ROOT/lib/")
    do
        _WG_CLASSPATH="${_WG_CLASSPATH}${_WG_CLASSPATH_DELIMITER}${_WG_ROOT}/lib/$f"
    done
fi

if [ "$ASAKUSA_HOME" != "" -a -d "$ASAKUSA_HOME/core/lib" ]
then
    for f in $(ls "$ASAKUSA_HOME/core/lib/")
    do
        _WG_CLASSPATH="${_WG_CLASSPATH}${_WG_CLASSPATH_DELIMITER}${ASAKUSA_HOME}/core/lib/$f"
    done
fi

if [ -d "$ASAKUSA_HOME/ext/lib" ]
then
    for f in $(ls "$ASAKUSA_HOME/ext/lib/")
    do
        _WG_CLASSPATH="${_WG_CLASSPATH}${_WG_CLASSPATH_DELIMITER}${ASAKUSA_HOME}/ext/lib/$f"
    done
fi
