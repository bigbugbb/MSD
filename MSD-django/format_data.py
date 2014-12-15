from __future__ import division
import sys
import csv
from random import randint

def format_data(input_path, output_path, profile=False, percent=100):

	# open the output file for writing the formatted data
	with open(output_path, 'a') as output:

		# open the input file to be processed
		with open(input_path, 'r', 32768) as csvfile:
			dialect = csv.Sniffer().sniff(csvfile.read(), delimiters=',\t')
			csvfile.seek(0)
			reader = csv.reader(csvfile, dialect)

			# get rid of duplicated item
			tracks = set()

			# start formating each line
			for row in reader:				

				# randomly skip data based on the incoming percent
				if randint(1, 100) > percent:
					continue

				# make the new format
				if profile:			
					if row[1] not in tracks:							
						new_row = ','.join([row[0], row[1], row[7]]) + '\n'
						tracks.add(row[1])
					else:
						continue
				else:					
					new_row = ','.join([row[0], row[7]]) + ',' + ' '.join(row[9:]) + '\n'

				# write the formatted data by line
				output.write(new_row)

def die_with_usage():
	""" HELP MENU """
	print('format_data.py')
	print('    by Bin Bo (2014) Northeastern University')
	print('       bigbugbb@gmail.com')
	print('Format input data into test/profile data acceptable by MSD-django')
	print('usage:')
	print('   python format_data.py [FLAGS] <INPUT> <OUTPUT>')
	print('FLAGS')
	print('  -profile      	  - format into profile data')
	print('  -percent n       - the amount of data in percent to be processed')
	print('INPUT')
	print('   input   - path of the input data file')
	print('OUTPUT')
	print('   output  - path of the output data file')
	sys.exit(0)

if __name__ == "__main__":

	# help menu
	if len(sys.argv) < 3:
	    die_with_usage()

	# flags
	format_profile = False
	percent = 100
	while True:
		if sys.argv[1] == '-profile':
			format_profile = True
		elif sys.argv[1] == '-percent':
			percent = int(sys.argv[2])
			sys.argv.pop(1)
		else:
			break
		sys.argv.pop(1)

	# input and output
	input_path  = sys.argv[1]
	output_path = sys.argv[2]

	# add params to dict
	params = {
		'profile': format_profile,
		'percent': percent, 
		'input_path': input_path, 
		'output_path': output_path
	}

	# verbose
	print('PARAMS:', params)

	# start formatting
	format_data(**params)

