This project is here to preserve backwards compatibility with old
versions of the mongo client.
When updating versions of the supported clients, 
we found that keeping the old instrumentation, and the new instrumentation in the same package
would impose a great maintenance burden.

The easiest solution was this: 
If you're using old versions of the client, use this project.
For newer ones, use `kamon-mongo`.

Of course, none of this applies if you're using `kamon-bundle`, in which case you can 
ignore all of this, since everything should work as it did.

Versions supported by this project:
```
mongodb-driver-sync <= 3.11.0
mongo-scala-driver <= 2.7.0
```
