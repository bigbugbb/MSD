MSD-django
==========
The backend of MSD project. MSD runs the Hadoop MapReduce jobs on EMR and output the results to S3. This one tries to get this output from S3 as csv, import them to postgres and provide a set of rest apis for the mobile app.

## Features
 * Powered by Django on EC2 and Postgres
 * Get the data from S3 by s3cmd
 * Import csv data to postgres table
 * Using test dataset to test the model trained on EMR for year prediction

## Quick Setup

#### 1. Create virtual environment

 * Upload the project to EC2 machine
 * Install virtualenv: sudo apt-get install python-virtualenv
 * Go to the project root directory and type: virtualenv env
 * Switch to the created virtual environment: source env/bin/activate

#### 2. Install postgres

 * Follow the postgres install and configuration part in this tutorial: https://www.digitalocean.com/community/tutorials/how-to-install-and-configure-django-with-postgres-nginx-and-gunicorn
 * Then upgrade your user to be a superuser: ALTER USER myuser WITH SUPERUSER;
 * Create the 'msd' database: psql -U YOURUSERNAME template1 -c "CREATE DATABASE msd;"
 * Open your django project settings.py and modify the 'DATABASES' part.

 **NOTE:** Your created user must be a superuser to create a database. If something doesn't work, make sure the 'HOST' in the 'DATABASES' is populated with '127.0.0.1'.

#### 3. Install dependent libraries

 * Intall from the requirements.txt file: pip install -r requirements.txt

#### 4. Make database migrations and csv import
 * Swtich back to the project root folder and type: python manager.py makemigrations
 * Run the migrations: python manager.py migrate
 * Execute the import data script: prepair_data.sh

#### 5. Start the server locally
 * Start the server locally: python manager.py runserver
 
#### 6. Start the server on EC2
 * Make sure apache2 is installed
 * Follow the apache part in this tutorial: https://www.digitalocean.com/community/tutorials/how-to-run-django-with-mod_wsgi-and-apache-with-a-virtualenv-python-environment-on-a-debian-vps
 * Start the server: sudo service apache2 restart

#### To host the webserver with python3 you need to do the following:
 * Execute: sudo apt-get install python3-dev
 * Execute: sudo apt-get install apache2-dev
 * Install mod_wsgi-4.4.0 (see this: https://code.google.com/p/modwsgi/wiki/QuickInstallationGuide)
 * Remember to modify the python path in the apache2 configuration file for your website
 * Also see this http://stackoverflow.com/questions/5930585/how-to-use-python-3-and-django-with-apache if you still have problems
