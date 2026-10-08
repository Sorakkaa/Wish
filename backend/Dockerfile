FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

# Expose the port the app runs on
EXPOSE 5000

# Start the application
CMD ["python", "app.py"]
