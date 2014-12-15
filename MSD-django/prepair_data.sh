#!/bin/sh

PWD=`pwd`
TEMP=tmp
USER=bigbug				   # change to your postgres user name
UPDATE_PASSWORD=msd-update
S3BUCKET=s3://msd-project
EC2_ADDRESS=localhost:8000 # change to your actual ec2 address

rm -rf tmp
mkdir debug
mkdir -p msd/meta

# Download all csv data from s3 and copy them into database tables
s3cmd get ${S3BUCKET}/ranges/part* ${TEMP}/ranges.csv
s3cmd get ${S3BUCKET}/counts/part* ${TEMP}/counts.csv
s3cmd get ${S3BUCKET}/model/part* ${TEMP}/nbmodel.csv
s3cmd get ${S3BUCKET}/testing/part* ${TEMP}/test_data.csv
s3cmd get ${S3BUCKET}/clusters/part* msd/meta/cluster_data.csv
echo 'import naivebayes_range table ...'
psql -U ${USER} msd -c "DELETE FROM naivebayes_range; COPY naivebayes_range FROM '${PWD}/${TEMP}/ranges.csv' CSV;"
echo 'import naivebayes_count table ...'
psql -U ${USER} msd -c "DELETE FROM naivebayes_count; COPY naivebayes_count(year, count, prob) FROM '${PWD}/${TEMP}/counts.csv' CSV;"
echo 'import naivebayes_nbmodel table ...'
psql -U ${USER} msd -c "DELETE FROM naivebayes_nbmodel; COPY naivebayes_nbmodel(year, fprob) FROM '${PWD}/${TEMP}/nbmodel.csv' CSV;"
echo 'import naivebayes_testdata table ...'
python format_data.py ${TEMP}/test_data.csv ${TEMP}/new_test_data.csv
psql -U ${USER} msd -c "DELETE FROM naivebayes_testresult; DELETE FROM naivebayes_testdata; COPY naivebayes_testdata(year, releaseid, features) FROM '${PWD}/${TEMP}/new_test_data.csv' CSV;"

# Do the test with the test dataset
java -jar test.jar ${TEMP}/ranges.csv ${TEMP}/counts.csv ${TEMP}/nbmodel.csv ${TEMP}/test_data.csv msd/meta/test_result.csv msd/meta/test_summary.csv

# Import the test result to database table
echo 'import naivebayes_testresult table ...'
curl -L -e '; auto' -s ${EC2_ADDRESS}/naivebayes/updates?password=${UPDATE_PASSWORD} > debug/import_test_result.html

# Get the song profile data by 7 digital api and import the data to database table
echo 'import song_profile table ...'
python format_data.py -profile -percent 1 ${TEMP}/test_data.csv ${TEMP}/profile_data.csv
psql -U ${USER} msd -c "DELETE FROM song_profile; COPY song_profile(year, trackid, releaseid) FROM '${PWD}/${TEMP}/profile_data.csv' CSV;"
echo 'update profiles by calling 7 digital API ...'
curl -L -e '; auto' -s ${EC2_ADDRESS}/song/updates/?password=${UPDATE_PASSWORD} > debug/fetch_profile_from_7digital.html

# Clean up
rm -rf tmp


