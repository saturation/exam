EXAM Quickstart for developers
=====================================

1.  Create postgresql database

        $ createdb sitnet --owner=sitnet
        
    For tests
    
        $ createdb sitnet_test --owner=sitnet
        
    For protractor
        
        $ createdb sitnet_protractor --owner=sitnet

    Requires that you have postgres installed, appropriate user created, pg_hba.conf set up to accept local connections etc).  
    Note that you can configure database related parameters (and others) in the dev config file (conf/dev.conf)

2.  Install SBT, for example from Homebrew

        $ brew install sbt

3.  Start sbt console
        
        $ sbt -Dconfig.resource=dev.conf -jvm-debug 9999

    This opens up a debug port at 9999.

4.  Start the app

        [exam] $ run

5.  Navigate to http://localhost:9000
    Accept the database migrations in case you see a prompt.



For more information see [official installation instructions](https://confluence.csc.fi/display/EXAM/Asennusohjeet) (in Finnish only)
