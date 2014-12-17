MSD
===

Song recommendation and year prediction application based on the data processing of Million Song Dataset from Amazon PDS with map reduce and data mining.

##
MSD-mapred contains all code for training the Naive Bayes model in java hadoop.

##
MSD-django is the server hosted on EC2. The output of MSD-mapred on S3 is imported to the django server's postgres database.

##
MSD-android is an android app communicating with MSD-django to fetch song profiles and respond to users' requests of year prediction and song recommandation.