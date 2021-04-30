### Description

- Rates will be fetched periodically by a scheduler.
- This time interval can be changed through `rates-refresh` config parameter
- At the moment this is set to `1 minute` 

### Running the project

- make sure the `one-frame` service is running at `localhost:8080` or change the `one-frame` config in `application.conf`
- run the `Main.scala` file
- Application will try to start at `localhost:9091`
- This port can be changed from `application.conf`