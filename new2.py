import json

json_string = """[
    {
        "id":"1",
        "age":"20",
        "weight":"81"
    },
    {
        "id":"2",
        "age":"19",
        "weight":"61"
    },
    {
        "id":"3",
        "age":"21",
        "weight":"51"
    },
    {
        "id":"4",
        "age":"29",
        "weight":"41"
    },
    {
        "id":"5",
        "age":"25",
        "weight":"91"
    }
]"""

json_data = json.loads(json_string)
for item in json_data:
    print(json.dumps(item))
