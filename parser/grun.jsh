///usr/bin/env jbang --cp target/classes "$0" "$@" ; exit $?
//DEPS org.antlr:antlr4:4.13.2

System.out.println(Arrays.asList(args));

import org.antlr.v4.gui.TestRig;

TestRig.main(args);