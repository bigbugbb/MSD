import os
import csv
import operator
from django.core import serializers
from django.http import HttpResponse
from msd.settings import PROJECT_ROOT
from naivebayes.models import Range, Count, NBModel, TestData, TestResult

TEST_RESULT = '/meta/test_result.csv'

def frange(start, end, count):
	"A range function, that does accept float increments..."
	import math

	if end == None:
		end = start + 0.0
		start = 0.0
	else: 
		start += 0.0 # force it to be a float

	if count == None:
		count = end - start        

	inc = float(end - start) / count
	L = [None,] * count
	for i in range(count):
		L[i] = start + i * inc

	return L

def range_index(request):
	ranges = Range.objects.all()
	jsonData = serializers.serialize("json", ranges)
	return HttpResponse(jsonData)

def range_detail(request, range_id):
	result = Range.objects.filter(id=range_id)
	jsonData = serializers.serialize("json", result)
	return HttpResponse(jsonData)

def nbmodel_index(request):
	ranges = NBModel.objects.all()
	jsonData = serializers.serialize("json", ranges)
	return HttpResponse(jsonData)

def nbmodel_detail(request, year):
	result = NBModel.objects.filter(year=year)
	jsonData = serializers.serialize("json", result)
	return HttpResponse(jsonData)

def count_index(request):
	counts = Count.objects.all()
	jsonData = serializers.serialize("json", counts)
	return HttpResponse(jsonData)

def count_detail(request, year):
	result = Count.objects.filter(year=year)
	jsonData = serializers.serialize("json", result)
	return HttpResponse(jsonData)

def update_naivebayes(request):
	# get all neccessary data to predict year
	# ranges  = [frange(rge.min, rge.max, rge.step) for rge in Range.objects.all()]
	# counts  = Count.objects.all()
	# nbmodel = [[float(x) for x in year_model.fprob.split(' ')] for year_model in NBModel.objects.all()]	

	# # for each piece of testdata, predict the year and save the result
	# data = TestData.objects.all()
	# for d in data:
	# 	features = [float(feature) for feature in d.features.split(' ')];
	# 	predict = predict_year_imp(features, ranges, counts, nbmodel)
	# 	d.testresult_set.all().delete()
	# 	d.testresult_set.create(result=predict, correct=(d.year-5 <= predict <= d.year+5))

	# check the request, we don't want to receive the request from the outside world
	if 'password' in request.GET:
		password = request.GET['password']
		if password != 'msd-update':
			return HttpResponse("Permission not allowed!\n")
	else:
		return HttpResponse("Permission not allowed!\n")

	# get the test data
	data = TestData.objects.all()

	# get the test result
	result = [None,] * len(data)
	with open(PROJECT_ROOT + TEST_RESULT, 'r') as result_file:
		dialect = csv.Sniffer().sniff(result_file.read(), delimiters=',')
		result_file.seek(0)
		reader = csv.reader(result_file, dialect)

		i = 0
		for row in reader:
			result[i] = (int(row[0]), int(row[1]))
			i += 1

	# import each test result associated with test data to database 
	i = 0
	for d in data:
		d.testresult_set.all().delete()
		d.testresult_set.create(result=result[i][1], correct=(d.year-3 <= result[i][1] <= d.year+3))
		i += 1

	return HttpResponse("Update NaiveBayes OK!\n")

