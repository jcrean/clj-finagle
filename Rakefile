namespace :thrift do
  desc "Compile thrift IDL to src/java"
  task :compile, :file do |t,args|
    file = args[:file]
    puts "running scrooge compiler on #{file}"
    system "whoami"
    system "java", "-jar", "/Users/jcrean/personal.projects/sandbox/finagle-thrift/scrooge/scrooge-generator/target/scrooge-generator-3.1.2-jar-with-dependencies.jar", "--finagle", "-l", "Java", "-d", "/Users/jcrean/personal.projects/sandbox/finagle-thrift/src/java/", file
  end
end
