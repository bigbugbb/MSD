import csv
import urllib.request
import xml.etree.ElementTree as etree
from django.shortcuts import render
from django.core import serializers
from django.http import HttpResponse
from django.db.models import Q
from song.models import Profile
from msd.settings import PROJECT_ROOT
from naivebayes.models import TestData, TestResult
from random import randint


URI_7DIGITAL_API = "http://api.7digital.com/1.2/release/details?releaseid={0}&country=gb&oauth_consumer_key=7d3tx4gj6s6p"
CLUSTER_DATA = '/meta/cluster_data.csv'

def profile_index(request):	
	# get all valid profiles and the total count of these profiles
	profiles = Profile.objects.filter(~Q(title__isnull=True) & ~Q(title__exact='') & Q(fromtest=True))
	count = len(profiles)
	print('profile count: {0}'.format(count))

	# create the indices to get the profile objects from database
	indices = range(0, count)

	# shuffle the indices because we want some randomness
	for n in range(0, count):
		# generate two random indices for data switching
		i = randint(0, count - 1)
		j = randint(0, count - 2)
		if i == j: j = count - 1

		# switch the values in indices[i] and indices[j]
		indices[i] = indices[i] ^ indices[j]
		indices[j] = indices[i] ^ indices[j]
		indices[i] = indices[i] ^ indices[j]

	# now we have a random indices list from 0 to count-1
	# it's time to fetch the profile
	shuffled = [profiles[i] for i in indices]

	# return the json format
	jsonData = serializers.serialize("json", shuffled)
	return HttpResponse(jsonData)

def update_data(request):
	# check the request, we don't want to receive the request from the outside world
	if 'password' in request.GET:
		password = request.GET['password']
		if password != 'msd-update':
			return HttpResponse("Permission not allowed!\n")
	else:
		return HttpResponse("Permission not allowed!\n")

	# get the test result
	clustered = {}
	with open(PROJECT_ROOT + CLUSTER_DATA, 'r') as cluster_file:
		dialect = csv.Sniffer().sniff(cluster_file.read(), delimiters=',')
		cluster_file.seek(0)
		reader = csv.reader(cluster_file, dialect)

		for row in reader:
			clustered[row[0]] = (int(row[1]), int(row[2]))

	# make sure all profiles of songs from test data are updated
	# because only from these data are year prediction available.
	for profile in Profile.objects.all():
		# since the API is limited to a rate of 4000 requests per day
		# if the profile's already been updated, skip the call		
		if profile.title != None: continue

		# get song info by from 7 digital api
		info = get_song_info(profile.releaseid)
		if info != None:
			# populate song profile
			profile.title  = info['title']
			profile.year   = info['year']
			profile.artist = info['artist']
			profile.image  = info['image']

			# update the centroid if possible			
			if profile.trackid in clustered:
				profile.centroid = clustered[profile.trackid][1]

			profile.fromtest = True # profile_index will only return profiles from test data

			# update the current profile
			profile.save(update_fields=['title', 'year', 'artist', 'image', 'centroid', 'fromtest'])

	# randomly select songs from the clustered, create then update their profiles
	for key, value in clustered.items():
		# keep the data small so less api calls are needed
		if randint(0, 999) < 990: continue

		# check whether the profile table has the current track id (key)
		if len(Profile.objects.filter(trackid=key)) > 0: continue

		# create a new profile with initial data
		profile = Profile.objects.create_profile(trackid=key, releaseid=value[0], centroid=value[1], fromtest=False)

		# update the profile by calling 7 digital api
		info = get_song_info(profile.releaseid)		
		if info != None:
			# populate song profile
			profile.title  = info['title']
			profile.year   = info['year']
			profile.artist = info['artist']
			profile.image  = info['image']
	
			# update the current profile
			profile.save(update_fields=['title', 'year', 'artist', 'image'])

	return HttpResponse('Update Profiles OK!\n')

def get_song_info(releaseid):
	uri = URI_7DIGITAL_API.format(releaseid)
	print('parse {0}'.format(uri))

	# open the uri and parse the responded xml 	
	tree = etree.parse(urllib.request.urlopen(uri))
	root = tree.getroot()		
	if root.findall('error'): return None

	# get the info we want
	info = {}
	info['title']  = root[0].findall('title')[0].text
	info['year']   = int(root[0].findall('year')[0].text)
	info['artist'] = root[0].findall('artist')[0].findall('name')[0].text
	info['image']  = root[0].findall('image')[0].text
	print('info:', info)
	
	return info

def predict_year(request, release_id):
	testdata  = TestData.objects.filter(releaseid=release_id)[0]
	predicted = testdata.testresult_set.all()[0].result
	return HttpResponse(predicted)

def recommend_songs(request, release_id):	
	# get the cluster the song belongs to
	clusters = []
	try:
		centroid = Profile.objects.filter(releaseid=release_id)[0].centroid
		if centroid != None:			
			clusters = Profile.objects.filter(Q(centroid=centroid) & ~Q(releaseid=release_id) & ~Q(image=None))[:30]
	except:
		pass

	# return the json format
	jsonData = serializers.serialize("json", clusters)
	return HttpResponse(jsonData)
