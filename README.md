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
       ;; generated service code via example.thift.
       ;; you'll need to import a couple of classes that were generated by Scrooge.
       [com.example Example Example$FutureIface Example$FinagledService Example$FinagledClient]))

    ;; Defines a processor for the Example service
    (def-processor :example-processor Example

      ;; Define how the ping() function (defined in example.thift)
      ;; will process requests.

      (ping [] (println "its alive!")))

#### Define the Finagle Client/Server

The next step is to actually define the RPC client/server for your service. This is accomplished via the `def-rpc` macro.

    (def-rpc :example
      :service    Example
      :name       "MyAwesomeService"
      :processor  :example-processor
      :port       8888)

This will start a Finagle server on the specified port and generate a client wrapper for making RPC calls into the service.

#### Define client API functions

The final step is to define the client API that will interact with your RPC service.

    (def-client-api ping []

      ;; Identify the RPC service that this client API interacts with.
      ;; Uses the logical name defined in def-rpc above.
      {:for-service :example}
    
      ;; Finagle is an asynchronous API, so it defines callbacks
      ;; for success and failure
      (onSuccess [return-val]  (println "Hey the server says he's alive (return value is nil since ping() returns void)"))
      (onFailure [throwable]   (println (format "Uh oh something went wrong: %s" throwable))))

## TODO

* Add example of defining thrift types that are used by the service

## License

Copyright © 2013

Distributed under the Eclipse Public License, the same as Clojure.
