#!/usr/bin/env python3
"""
Mock API server for Loli Daily.
Returns random colored images with numbers to verify API calls.
"""

import io
import random
from flask import Flask, jsonify, send_file
from PIL import Image, ImageDraw, ImageFont

app = Flask(__name__)

# In-memory storage for the daily data
current_date = None
current_cards = []


def generate_random_color():
    """Generate a random RGB color."""
    return (
        random.randint(50, 255),
        random.randint(50, 255),
        random.randint(50, 255)
    )


def create_image_with_number(number, width=800, height=1200):
    """Create an image with a random background color and a number in the center."""
    # Random background color
    bg_color = generate_random_color()
    
    # Create image
    img = Image.new('RGB', (width, height), bg_color)
    draw = ImageDraw.Draw(img)
    
    # Draw the number in the center
    text = str(number)
    
    # Try to use a font, fallback to default
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 120)
    except:
        try:
            font = ImageFont.truetype("C:\\Windows\\Fonts\\arial.ttf", 120)
        except:
            font = ImageFont.load_default()
    
    # Get text bounding box
    bbox = draw.textbbox((0, 0), text, font=font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    
    # Calculate position to center the text
    x = (width - text_width) // 2
    y = (height - text_height) // 2
    
    # Draw text with a contrasting color
    text_color = (255 - bg_color[0], 255 - bg_color[1], 255 - bg_color[2])
    draw.text((x, y), text, fill=text_color, font=font)
    
    # Add "MOCK" label at top
    draw.text((10, 10), "MOCK", fill=text_color, font=font)
    
    # Add timestamp
    import datetime
    timestamp = datetime.datetime.now().strftime("%H:%M:%S")
    draw.text((10, height - 30), timestamp, fill=text_color, font=font)
    
    return img


def generate_daily_data():
    """Generate new daily data with random images."""
    global current_date, current_cards
    
    import datetime
    today = datetime.datetime.now().strftime("%Y-%m-%d")
    
    # Always generate new data (ignore date check for mock)
    current_date = today
    
    # Generate 5 random cards
    cards = []
    for i in range(5):
        number = random.randint(1000, 9999)
        # Create a temporary image to get the URL
        cards.append({
            "imgUrl": f"http://localhost:5000/image/{number}",
            "artistName": f"Mock Artist {number}",
            "comment": f"Mock image #{number}",
            "tags": random.choice(["LC0", "LC YJ", "LC ES"]),
            "characterNames": [f"Character {number}"],
            "sourceUrl": f"https://mock.example.com/{number}",
            "artistUrl": f"https://mock.example.com/artist/{number}",
            "suggestedBy": {"nickname": "MockUser"}
        })
    
    current_cards = cards
    return today, cards


@app.route('/api/v1/daily')
def get_daily():
    """Return daily data with mock images."""
    global current_date, current_cards
    
    # Generate new data on each call (for testing)
    today, cards = generate_daily_data()
    
    response = {
        "cards": cards,
        "date": today
    }
    
    print(f"[MOCK] Returning daily data with {len(cards)} cards for {today}")
    return jsonify(response)


@app.route('/api/v1/daily/react')
def get_reactions():
    """Return mock reaction data."""
    reactions = []
    for i in range(5):
        reactions.append({
            str(random.choice([0, 54, 80, 88, 90, 104, 122, 140])): [
                {"username": f"user{j}"} for j in range(random.randint(0, 5))
            ]
        })
    
    return jsonify({"reactions": reactions})


@app.route('/image/<int:number>')
def get_image(number):
    """Return a mock image with the number drawn on it."""
    img = create_image_with_number(number)
    
    # Save to bytes
    img_io = io.BytesIO()
    img.save(img_io, 'JPEG', quality=85)
    img_io.seek(0)
    
    print(f"[MOCK] Serving image with number {number}")
    return send_file(img_io, mimetype='image/jpeg')


@app.route('/')
def index():
    return """
    <html>
    <body>
        <h1>Mock API Server for Loli Daily</h1>
        <p>Endpoints:</p>
        <ul>
            <li><a href="/api/v1/daily">/api/v1/daily</a> - Get daily data</li>
            <li><a href="/api/v1/daily/react">/api/v1/daily/react</a> - Get reactions</li>
            <li>/image/&lt;number&gt; - Get mock image</li>
        </ul>
        <p>Each call to /api/v1/daily generates new random images!</p>
    </body>
    </html>
    """


if __name__ == '__main__':
    print("Starting Mock API Server...")
    print("Daily API: http://localhost:5000/api/v1/daily")
    print("Image URL: http://localhost:5000/image/<number>")
    app.run(host='0.0.0.0', port=5000, debug=True)
