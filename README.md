# Madek ZHdK LDAP Sync

An application to sync groups from ZHdK LDAP to a Madek instance, i.e. https://medienarchiv.zhdk.ch/.

This serves also as an example how to use the Madek-API to manage institutional-groups.

## Usage

In general: `java -jar madek-zhdk-ldap-sync.jar [args]`.

The values for `MADEK_TOKEN` and `LDAP_PASSWORD` will also be read from
environment variables. This is considered to be more secure then giving program
arguments.


### Real Sync Example

    $ java -jar madek-zhdk-ldap-sync.jar --help

    $ java -jar target/madek-zhdk-ldap-sync.jar --output-file tmp/data_2018-01.json  --ldap-password '**********'

    $ java -jar target/madek-zhdk-ldap-sync.jar --input-file tmp/data_2018-01.json  -t ******************************** > tmp/test-change.log

    $ java -jar target/madek-zhdk-ldap-sync.jar --input-file tmp/data_2018-01.json  -t ******************************** -u https://medienarchiv.zhdk.ch > tmp/change.log


### ZHdK LDAP System Account

See the (encrypted) file `inventories/zhdk/README_ZHdK_secrets.md` in the deploy project.


## Building

    $ ./bin/clj-uberjar


## Development

see

    ./bin/deploy-to-test
    ./bin/deploy-to-prod



### Start a REPL

    $ ./bin/clj-run


### Examples for running the source directly:

    $ ./bin/clj-run --output-file tmp/test.json

    $ ./bin/clj-run --delete --input-file tmp/test.json --madek-base-url http://localhost:3100


## Limitations, Problems and Bugs

A misleading error message `JsonParseException` will be given when the Madek
server is configured to accept https only (by providing redirects) if the wrong
protocol `http` is used.
