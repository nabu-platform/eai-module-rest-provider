There are a number of approaches to caching rest services. We could provide an interface where for any given input you generate the etag and/or last modified as output of the interface.

If the client provides them, we can then run this service first, compare them and conclude whether or not we need to run the service again. Downside is that you always have to return a "valid" date or etag, this will presumably incur at least a lookup.

However, lets look at the datastore, because resources are (generally) immutable, once you have them (so the client has _a_ last modified or etag), it will always be correct, we can even skip the lookup.

Additionally suppose we conclude that you indeed need a new copy, we call the rest service, but we have no guarantee at that point that the etag/last modified we calculated earlier matches the service output, they are two separate service calls that hopefully match.

Alternative:

An interface where we pass in the last modified and/or etag provided by the client and a boolean "hasChanged" as output. You can internalize the logic of validating the input (for datastore it would simply set true in the output).

If it has indeed changed, we call the rest service and we inject a last modified & etag value option in the output.

This provides more flexibility and combines the output generation with the etag/modified generation in one service.