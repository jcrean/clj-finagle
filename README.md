# clj-finagle

A Clojure wrapper around Twitter's Finagle RPC server/client

## Usage

Finagle is an asynchronous network stack for building RPC clients/servers. See https://github.com/twitter/finagle. This project aims to simplify some of the code needed to generate and work with these clients and servers.

Finagle server/client interfaces can be generated by running Twitter's Scrooge compiler on Thrift IDL files. The first step is to create a Thrift IDL file describing your service. The following example will create the bare mininum to demonstrate usage of this library. For more information, check out http://thrift.apache.org.

#### Create Thrift IDL file to describe your service

    $ vi src/thrift/example.thrift

    namespace java com.example

    service Example {
      void ping()
    }

#### Generate source code

Use the Scrooge compiler to generate Finagle client/server interfaces. You can run scrooge using the lein-scrooge plugin. See http://github.com/jcrean/lein-scrooge for details on setting up your project to use the plugin.

Once you have installed lein-scrooge and configured your project file (be sure to set `finagle: true`), just run

    $ lein scrooge
    $ lein javac

This will generate source code into src/java (or wherever you've configured scrooge to place generated source) and compile.

#### Define thrift processor

You need to define how your RPC server will process requests by defining a `processor` (see http://thrift.apache.org/docs/concepts/ for more info).

    (ns my-namespace
      (:use
       clj-finagle.core)
      (:import
       ;; generated service code via example.thift
       [com.example Example]))
    
    ;; Defines a processor for the Example service
    (def-processor :example-processor Example
    
      ;; Define how the ping() function (defined in example.thift)
      ;; will process requests.
    
      (ping [] (println "its alive!")))

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
