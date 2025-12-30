# Render Deployment Guide

This guide will walk you through deploying both the frontend and backend to Render's free tier.

## Prerequisites

1. A GitHub account
2. Your code pushed to a GitHub repository
3. A Render account (sign up at https://render.com - it's free)
4. An OpenAI API key (if you plan to use AI features)

## Step 1: Push Your Code to GitHub

If you haven't already, push your code to a GitHub repository:

```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin <your-github-repo-url>
git push -u origin main
```

## Step 2: Deploy Backend Service

1. **Go to Render Dashboard**: https://dashboard.render.com
2. **Click "New +"** → **"Web Service"**
3. **Connect your GitHub repository** (if not already connected)
4. **Select your repository** from the list
5. **Configure the service**:
   - **Name**: `codesys-backend`
   - **Region**: Choose closest to you (e.g., Frankfurt)
   - **Branch**: `main` (or your default branch)
   - **Root Directory**: `backend`
   - **Environment**: `Java`
   - **Build Command**: `mvn clean package -DskipTests`
   - **Start Command**: `java -jar target/codesys-backend-0.0.1-SNAPSHOT.jar`
   - **Plan**: `Free`

6. **Add Environment Variables**:
   - `SPRING_PROFILES_ACTIVE` = `production`
   - `SERVER_URL` = `https://codesys-backend.onrender.com` (use your actual backend URL - this is used for Swagger UI)
   - `OPENAI_API_KEY` = `<your-openai-api-key>` (if using AI features)
   - `FHIR_SERVER_URL` = `https://snowstorm-training.snomedtools.org/fhir`
   - `JAVA_VERSION` = `17`
   
   **Note**: If you're using `render.yaml` for deployment, `SERVER_URL` will be set automatically. Otherwise, set it manually after deployment using the URL from step 9.

7. **Click "Create Web Service"**

8. **Wait for deployment** - Render will build and deploy your backend. This may take 5-10 minutes.

9. **Note the backend URL** - It will be something like `https://codesys-backend.onrender.com`

## Step 3: Deploy Frontend Service

1. **In Render Dashboard**, click **"New +"** → **"Static Site"**
2. **Select your repository** (same one as backend)
3. **Configure the service**:
   - **Name**: `codesys-frontend`
   - **Region**: Same as backend
   - **Branch**: `main`
   - **Root Directory**: `frontend`
   - **Build Command**: `npm install && npm run build`
   - **Publish Directory**: `dist`
   - **Plan**: `Free`

4. **Add Environment Variables**:
   - `VITE_API_URL` = `https://codesys-backend.onrender.com` (use your actual backend URL from Step 2)
   - `NODE_VERSION` = `18`

5. **Click "Create Static Site"**

6. **Wait for deployment** - This should be faster, around 2-5 minutes.

7. **Note the frontend URL** - It will be something like `https://codesys-frontend.onrender.com`

## Step 4: Update Frontend API URL (if needed)

If you need to update the backend URL after deployment:

1. Go to your frontend service in Render dashboard
2. Click on **"Environment"** tab
3. Update `VITE_API_URL` to your backend URL
4. Click **"Save Changes"** - this will trigger a rebuild

## Alternative: Using render.yaml (Infrastructure as Code)

If you prefer to use the `render.yaml` file included in this project:

1. **Go to Render Dashboard** → **"New +"** → **"Blueprint"**
2. **Connect your GitHub repository**
3. **Select the repository** containing `render.yaml`
4. **Render will automatically detect and configure both services**
5. **Review the configuration** and click **"Apply"**
6. **Set the `OPENAI_API_KEY`** environment variable in the backend service settings

## Important Notes

### Free Tier Limitations

- **Backend**: Services on the free tier will **spin down after 15 minutes of inactivity**
- **First request after spin-down**: May take 30-60 seconds to wake up
- **Monthly limits**: 750 hours/month (enough for continuous operation of one service)
- **Both services**: You can run both frontend and backend on free tier, but they share the 750 hours

### CORS Configuration

The backend already has CORS enabled (`@CrossOrigin` on all controllers), so the frontend can make API calls directly to the backend URL.

### Environment Variables

Make sure to set all required environment variables:
- `OPENAI_API_KEY`: Required if using AI features (get from https://platform.openai.com)
- `FHIR_SERVER_URL`: Already set to the default SNOMED training server
- `VITE_API_URL`: Frontend needs this to know where the backend is

### Custom Domain (Optional)

You can add a custom domain to either service:
1. Go to service settings
2. Click **"Custom Domains"**
3. Add your domain and follow DNS configuration instructions

## Troubleshooting

### Backend won't start
- Check build logs in Render dashboard
- Verify Java version is 17
- Check that `OPENAI_API_KEY` is set if AI features are enabled
- Verify Maven build completes successfully

### Frontend can't connect to backend
- Verify `VITE_API_URL` is set correctly in frontend environment variables
- Check that backend URL is accessible (visit it in browser)
- Verify CORS is enabled (it should be by default)
- Check browser console for CORS errors

### Services are slow to respond
- Free tier services spin down after inactivity
- First request after spin-down takes time to wake up
- Consider upgrading to paid tier for always-on services

### Build fails
- Check build logs for specific errors
- Verify all dependencies are in `pom.xml` (backend) or `package.json` (frontend)
- Ensure Node.js version matches (18)
- Ensure Java version matches (17)

## Testing Your Deployment

1. **Test Backend**: Visit `https://your-backend-url.onrender.com/api/terms/match` (should return an error for GET, but confirms it's running)
2. **Test Frontend**: Visit `https://your-frontend-url.onrender.com` - should load the app
3. **Test Integration**: Try matching some terms in the frontend to verify API connectivity

## Updating Your Deployment

When you push changes to your GitHub repository:
- Render will automatically detect changes
- It will rebuild and redeploy your services
- You can also manually trigger deployments from the Render dashboard

## Support

- Render Documentation: https://render.com/docs
- Render Community: https://community.render.com
- Render Status: https://status.render.com

