MSD
===

Song recommendation and year prediction application backed by the data process on Million Song Dataset from Amazon PDS by using map reduce and data mining.

##
MSD-mapred contains all code for training the Naive Bayes model in java hadoop.

##
MSD-django is the server hosted on EC2. The output of MSD-mapred on S3 is imported to the django server's postgres database.

##
MSD-android is an android phone which communicate with the django server to get the song profiles, respond to user's requests of year prediction and song recommandation.