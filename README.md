# Madek ZHdK LDAP Sync

An application to sync groups from ZHdK LDAP to a Madek instance, i.e. https://medienarchiv.zhdk.ch/.

This serves also as an example how to use the Madek-API to manage institutional-groups.

## Usage


    $ java -jar madek-ldap.jar [args]

    $ java -jar madek-ldap.jar --input-file tmp/test.json --madek-token MADEK_TOKEN --madek-base-url http://localhost:3100

The values for `MADEK_TOKEN` and `LDAP_PASSWORD` will also be read from
environment variables. This is considered to be more secure then giving program
arguments.

## Development

Examples for running the source directly:

    $ lein run -- --output-file tmp/test.json

    $ lein run -- --delete --input-file tmp/test.json --madek-base-url http://localhost:3100


## Limitations, Problems and Bugs

A misleading error message `JsonParseException` will be given when the Madek
server is configured to accept https only (by providing redirects) if the wrong
protocol `http` is used.
